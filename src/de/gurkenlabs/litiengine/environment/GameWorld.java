package de.gurkenlabs.litiengine.environment;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import de.gurkenlabs.litiengine.Game;
import de.gurkenlabs.litiengine.IUpdateable;
import de.gurkenlabs.litiengine.environment.tilemap.IMap;
import de.gurkenlabs.litiengine.graphics.ICamera;
import de.gurkenlabs.litiengine.resources.Resources;

public final class GameWorld implements IUpdateable {
  private final List<EnvironmentLoadedListener> loadedListeners = new CopyOnWriteArrayList<>();
  private final List<EnvironmentUnloadedListener> unloadedListeners = new CopyOnWriteArrayList<>();
  private final Map<String, Collection<EnvironmentListener>> environmentListeners = new ConcurrentHashMap<>();
  private final Map<String, Collection<EnvironmentLoadedListener>> environmentLoadedListeners = new ConcurrentHashMap<>();
  private final Map<String, Collection<EnvironmentUnloadedListener>> environmentUnloadedListeners = new ConcurrentHashMap<>();
  private final Map<String, Collection<IUpdateable>> updatables = new ConcurrentHashMap<>();

  private final Map<String, IEnvironment> environments = new ConcurrentHashMap<>();

  private IEnvironment environment;
  private ICamera camera;

  @Override
  public void update() {
    if (this.environment() == null) {
      return;
    }

    if (this.environment().getMap() != null && this.environment().getMap().getName() != null) {
      String mapName = this.environment().getMap().getName().toLowerCase();
      if (this.updatables.containsKey(mapName)) {
        for (IUpdateable updatable : this.updatables.get(mapName)) {
          updatable.update();
        }
      }
    }
  }

  public void addLoadedListener(EnvironmentLoadedListener listener) {
    this.loadedListeners.add(listener);
  }

  public void removeLoadedListener(EnvironmentLoadedListener listener) {
    this.loadedListeners.remove(listener);
  }

  public void addUnloadedListener(EnvironmentUnloadedListener listener) {
    this.unloadedListeners.add(listener);
  }

  public void removeUnloadedListener(EnvironmentUnloadedListener listener) {
    this.unloadedListeners.remove(listener);
  }

  public void addLoadedListener(String mapName, EnvironmentLoadedListener listener) {
    add(this.environmentLoadedListeners, mapName, listener);
  }

  public void removeLoadedListener(String mapName, EnvironmentLoadedListener listener) {
    remove(this.environmentLoadedListeners, mapName, listener);
  }

  public void addUnloadedListener(String mapName, EnvironmentUnloadedListener listener) {
    add(this.environmentUnloadedListeners, mapName, listener);
  }

  public void removeUnloadedListener(String mapName, EnvironmentUnloadedListener listener) {
    add(this.environmentUnloadedListeners, mapName, listener);
  }

  public void addListener(String mapName, EnvironmentListener listener) {
    add(this.environmentListeners, mapName, listener);
  }

  public void removeListener(String mapName, EnvironmentListener listener) {
    remove(this.environmentListeners, mapName, listener);
  }

  public void attach(String mapName, IUpdateable updateable) {
    add(this.updatables, mapName, updateable);
  }

  public void detach(String mapName, IUpdateable updateable) {
    remove(this.updatables, mapName, updateable);
  }

  public ICamera camera() {
    return this.camera;
  }

  public void clear() {
    this.unloadEnvironment();
    this.environments.clear();
    this.setCamera(null);

    this.environmentListeners.clear();
    this.environmentLoadedListeners.clear();
    this.environmentUnloadedListeners.clear();

    this.loadedListeners.clear();
    this.unloadedListeners.clear();
  }

  public IEnvironment environment() {
    return this.environment;
  }

  public Collection<IEnvironment> getEnvironments() {
    return this.environments.values();
  }

  public IEnvironment getEnvironment(String mapName) {
    if (mapName == null || mapName.isEmpty()) {
      return null;
    }

    IMap map = Resources.maps().get(mapName);
    return this.getEnvironment(map);
  }

  public IEnvironment getEnvironment(IMap map) {
    if (map == null || map.getName() == null || map.getName().isEmpty()) {
      return null;
    }

    IEnvironment env = this.getEnvironments().stream().filter(e -> e.getMap().equals(map)).findFirst().orElse(null);
    if (env != null) {
      return env;
    }

    env = new Environment(map);
    this.addEnvironment(env);

    return env;
  }

  public boolean containsEnvironment(String identifier) {
    return this.environments.containsKey(identifier.toLowerCase());
  }

  public void loadEnvironment(final IEnvironment env) {
    unloadEnvironment();

    if (env != null) {
      this.addEnvironment(env);

      env.load();
      for (final EnvironmentLoadedListener listener : this.loadedListeners) {
        listener.loaded(env);
      }

      // call map specific listeners
      String mapName = getMapName(env);
      if (mapName != null && this.environmentLoadedListeners.containsKey(mapName)) {
        for (EnvironmentLoadedListener listener : this.environmentLoadedListeners.get(mapName)) {
          listener.loaded(env);
        }
      }
    }

    this.environment = env;
  }

  public IEnvironment loadEnvironment(String mapName) {
    IEnvironment env = this.getEnvironment(mapName);
    this.loadEnvironment(env);
    return env;
  }

  public IEnvironment loadEnvironment(IMap map) {
    IEnvironment env = this.getEnvironment(map);
    this.loadEnvironment(env);
    return env;
  }

  public void unloadEnvironment() {
    if (this.environment() != null) {
      this.environment().unload();

      for (final EnvironmentUnloadedListener listener : this.unloadedListeners) {
        listener.unloaded(this.environment());
      }

      // call map specific listeners
      String mapName = getMapName(this.environment());
      if (mapName != null && this.environmentUnloadedListeners.containsKey(mapName)) {
        for (EnvironmentUnloadedListener listener : this.environmentUnloadedListeners.get(mapName)) {
          listener.unloaded(this.environment());
        }
      }
    }

    this.environment = null;
  }

  public IEnvironment reset(String mapName) {
    if (mapName == null || mapName.isEmpty()) {
      return null;
    }

    return this.getEnvironment(Resources.maps().get(mapName));
  }

  public IEnvironment reset(IMap map) {
    if (map == null) {
      return null;
    }

    IEnvironment env = this.getEnvironment(map);
    if (env != null) {
      String mapName = getMapName(env);
      if (mapName != null) {
        this.environments.remove(mapName);

        // unwire all registered listeners for this particular map
        if (this.environmentListeners.containsKey(mapName)) {
          for (EnvironmentListener listener : this.environmentListeners.get(mapName)) {
            env.removeListener(listener);
          }
        }
      }
    }

    return this.getEnvironment(map);
  }

  public void setCamera(final ICamera cam) {
    if (this.camera() != null) {
      Game.loop().detach(camera);
    }

    camera = cam;

    if (!Game.isInNoGUIMode()) {
      Game.loop().attach(cam);
      this.camera().updateFocus();
    }
  }

  private static <T> void add(Map<String, Collection<T>> listeners, String mapName, T listener) {
    if (mapName == null || mapName.isEmpty()) {
      return;
    }

    String mapIdentifier = mapName.toLowerCase();
    if (!listeners.containsKey(mapIdentifier)) {
      listeners.put(mapIdentifier, Collections.synchronizedCollection(ConcurrentHashMap.newKeySet()));
    }

    listeners.get(mapIdentifier).add(listener);
  }

  private static <T> void remove(Map<String, Collection<T>> listeners, String mapName, T listener) {
    if (mapName == null || mapName.isEmpty()) {
      return;
    }

    String mapIdentifier = mapName.toLowerCase();
    if (!listeners.containsKey(mapIdentifier)) {
      return;
    }

    listeners.get(mapIdentifier).remove(listener);
  }

  private static String getMapName(IEnvironment env) {
    if (env.getMap() != null && env.getMap().getName() != null) {
      return env.getMap().getName().toLowerCase();
    }

    return null;
  }

  private void addEnvironment(IEnvironment env) {
    String mapName = getMapName(env);
    if (mapName == null) {
      return;
    }

    if (this.containsEnvironment(mapName)) {
      return;
    }

    this.environments.put(mapName, env);

    // wire up all previously registered listeners
    if (this.environmentListeners.containsKey(mapName)) {
      for (EnvironmentListener listener : this.environmentListeners.get(mapName)) {
        env.addListener(listener);
      }
    }
  }
}
