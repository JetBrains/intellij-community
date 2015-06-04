/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.util;

import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import com.intellij.ui.ScreenUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.Map;
import java.util.TreeMap;

/**
 * @author Sergey.Malenkov
 */
public abstract class WindowStateService {
  @NonNls private static final String KEY = "key";
  @NonNls private static final String STATE = "state";
  @NonNls private static final String X = "x";
  @NonNls private static final String Y = "y";
  @NonNls private static final String WIDTH = "width";
  @NonNls private static final String HEIGHT = "height";
  @NonNls private static final String EXTENDED = "extended-state";

  /**
   * @return an instance of the service for the application
   */
  public static WindowStateService getInstance() {
    return ServiceManager.getService(WindowStateService.class);
  }

  /**
   * @param project the project to use by the service
   * @return an instance of the service for the specified project
   */
  public static WindowStateService getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, WindowStateService.class);
  }

  /**
   * Loads a state of the specified component by the specified key.
   *
   * @param key       an unique string key
   * @param component a component which state should be changed
   * @return {@code true} if a state is loaded successfully, {@code false} otherwise
   */
  public final boolean loadState(@NotNull String key, @NotNull Component component) {
    return loadStateOn(null, key, component);
  }

  /**
   * Loads a state of the specified component by the given screen and the specified key.
   *
   * @param screen    a screen to which a location belongs
   * @param key       an unique string key
   * @param component a component which state should be changed
   * @return {@code true} if a state is loaded successfully, {@code false} otherwise
   */
  public boolean loadStateOn(GraphicsDevice screen, @NotNull String key, @NotNull Component component) {
    return false;
  }

  /**
   * Stores the specified location that corresponds to the specified key.
   * If it is {@code null} the stored location will be removed.
   *
   * @param key       an unique string key
   * @param component a component which state should be saved
   */
  public final void saveState(@NotNull String key, @NotNull Component component) {
    saveStateOn(null, key, component);
  }

  /**
   * Stores the specified location that corresponds to the given screen and the specified key.
   * If it is {@code null} the stored location will be removed.
   * Do not use a screen which is calculated from the specified component.
   *
   * @param screen    a screen to which a location belongs
   * @param key       an unique string key
   * @param component a component which state should be saved
   */
  public void saveStateOn(GraphicsDevice screen, @NotNull String key, @NotNull Component component) {
  }

  /**
   * Returns a location that corresponds to the specified key or {@code null}
   * if a location does not exist or it is outside of visible area.
   *
   * @param key an unique string key
   * @return a corresponding location
   */
  public final Point getLocation(@NotNull String key) {
    return getLocationOn(null, key);
  }

  /**
   * Returns a location that corresponds to the given screen and the specified key or {@code null}
   * if a location does not exist or it is outside of visible area.
   *
   * @param screen a screen to which a location belongs
   * @param key    an unique string key
   * @return a corresponding location
   */
  abstract Point getLocationOn(GraphicsDevice screen, @NotNull String key);

  /**
   * Stores the specified location that corresponds to the specified key.
   * If it is {@code null} the stored location will be removed.
   *
   * @param key an unique string key
   */
  public final void putLocation(@NotNull String key, Point location) {
    putLocationOn(null, key, location);
  }

  /**
   * Stores the specified location that corresponds to the given screen and the specified key.
   * If it is {@code null} the stored location will be removed.
   * Do not use a screen which is calculated from the specified location.
   *
   * @param screen a screen to which a location belongs
   * @param key    an unique string key
   */
  abstract void putLocationOn(GraphicsDevice screen, @NotNull String key, Point location);

  /**
   * Returns a size that corresponds to the specified key or {@code null}
   * if a size does not exist.
   *
   * @param key an unique string key
   * @return a corresponding size
   */
  public final Dimension getSize(@NotNull String key) {
    return getSizeOn(null, key);
  }

  /**
   * Returns a size that corresponds to the given screen and the specified key or {@code null}
   * if a size does not exist.
   *
   * @param screen a screen to which a size belongs
   * @param key    an unique string key
   * @return a corresponding size
   */
  abstract Dimension getSizeOn(GraphicsDevice screen, @NotNull String key);

  /**
   * Stores the specified size that corresponds to the specified key.
   * If it is {@code null} the stored size will be removed.
   *
   * @param key an unique string key
   */
  public final void putSize(@NotNull String key, Dimension size) {
    putSizeOn(null, key, size);
  }

  /**
   * Stores the specified size that corresponds to the given screen and the specified key.
   * If it is {@code null} the stored size will be removed.
   * Do not use a screen which is calculated from the specified size.
   *
   * @param screen a screen to which a size belongs
   * @param key    an unique string key
   */
  abstract void putSizeOn(GraphicsDevice screen, @NotNull String key, Dimension size);

  /**
   * Returns a bounds that corresponds to the specified key or {@code null}
   * if a bounds does not exist or it is outside of visible area.
   *
   * @param key an unique string key
   * @return a corresponding bounds
   */
  public final Rectangle getBounds(@NotNull String key) {
    return getBoundsOn(null, key);
  }

  /**
   * Returns a bounds that corresponds to the given screen and the specified key or {@code null}
   * if a bounds does not exist or it is outside of visible area.
   *
   * @param screen a screen to which a bounds belongs
   * @param key    an unique string key
   * @return a corresponding bounds
   */
  abstract Rectangle getBoundsOn(GraphicsDevice screen, @NotNull String key);

  /**
   * Stores the specified bounds that corresponds to the specified key.
   * If it is {@code null} the stored bounds will be removed.
   *
   * @param key an unique string key
   */
  public final void putBounds(@NotNull String key, Rectangle bounds) {
    putBoundsOn(null, key, bounds);
  }

  /**
   * Stores the specified bounds that corresponds to the given screen and the specified key.
   * If it is {@code null} the stored bounds will be removed.
   * Do not use a screen which is calculated from the specified bounds.
   *
   * @param screen a screen to which a bounds belongs
   * @param key    an unique string key
   */
  abstract void putBoundsOn(GraphicsDevice screen, @NotNull String key, Rectangle bounds);

  static final class WindowState {
    private Point myLocation;
    private Dimension mySize;
    private Integer myState;

    public Point getLocation() {
      return myLocation == null ? null : new Point(myLocation);
    }

    public Dimension getSize() {
      return mySize == null ? null : new Dimension(mySize);
    }

    public Rectangle getBounds() {
      return myLocation == null || mySize == null ? null : new Rectangle(myLocation, mySize);
    }

    public Integer getExtendedState() {
      return myState;
    }

    private void set(Point location, boolean locationSet, Dimension size, boolean sizeSet, Integer state, boolean stateSet) {
      if (locationSet) {
        myLocation = location == null ? null : new Point(location);
      }
      if (sizeSet) {
        mySize = size == null ? null : new Dimension(size);
      }
      if (stateSet) {
        myState = state;
      }
    }

    private boolean isEmpty() {
      return myLocation == null && mySize == null && myState == null;
    }

    private boolean isVisible() {
      if (myLocation == null) {
        return mySize != null;
      }
      if (ScreenUtil.isVisible(myLocation)) {
        return true;
      }
      if (mySize == null) {
        return false;
      }
      return ScreenUtil.isVisible(new Rectangle(myLocation, mySize));
    }
  }

  static class Service extends WindowStateService implements PersistentStateComponent<Element> {
    final Map<String, WindowState> myStateMap = new TreeMap<String, WindowState>();

    WindowState getOn(GraphicsDevice screen, @NotNull String key) {
      WindowState state = getImpl(getKey(screen, key));
      if (state == null) {
        state = getImpl(key);
        if (state == null) {
          state = new WindowState();
          state.myLocation = getDefaultLocationOn(screen, key);
          state.mySize = getDefaultSizeOn(screen, key);
          state.myState = getDefaultExtendedStateOn(screen, key);
          if (!state.isVisible()) {
            return null;
          }
        }
      }
      return state;
    }

    WindowState getImpl(@NotNull String key) {
      synchronized (myStateMap) {
        WindowState state = myStateMap.get(key);
        return state != null && state.isVisible() ? state : null;
      }
    }

    void putOn(GraphicsDevice screen, @NotNull String key,
               Point location, boolean locationSet,
               Dimension size, boolean sizeSet,
               Integer extendedState, boolean extendedStateSet) {
      synchronized (myStateMap) {
        putImpl(getKey(screen, key), location, locationSet, size, sizeSet, extendedState, extendedStateSet);
        putImpl(key, location, locationSet, size, sizeSet, extendedState, extendedStateSet);
      }
    }

    private void putImpl(@NotNull String key,
                         Point location, boolean locationSet,
                         Dimension size, boolean sizeSet,
                         Integer extendedState, boolean extendedStateSet) {
      WindowState state = myStateMap.get(key);
      if (state != null) {
        state.set(location, locationSet, size, sizeSet, extendedState, extendedStateSet);
        if (state.isEmpty()) {
          myStateMap.remove(key);
        }
      }
      else {
        state = new WindowState();
        state.set(location, locationSet, size, sizeSet, extendedState, extendedStateSet);
        if (!state.isEmpty()) {
          myStateMap.put(key, state);
        }
      }
    }

    @Override
    Point getLocationOn(GraphicsDevice screen, @NotNull String key) {
      Point location = getLocationImpl(getKey(screen, key));
      if (location != null) {
        return location;
      }
      location = getLocationImpl(key);
      if (location != null) {
        return location;
      }
      return getDefaultLocationOn(screen, key);
    }

    Point getDefaultLocationOn(GraphicsDevice screen, @NotNull String key) {
      return null;
    }

    Point getLocationImpl(@NotNull String key) {
      synchronized (myStateMap) {
        WindowState state = myStateMap.get(key);
        return state == null || !state.isVisible() ? null : state.getLocation();
      }
    }

    @Override
    void putLocationOn(GraphicsDevice screen, @NotNull String key, Point location) {
      putOn(screen, key, location, true, null, false, null, false);
    }

    @Override
    Dimension getSizeOn(GraphicsDevice screen, @NotNull String key) {
      Dimension size = getSizeImpl(getKey(screen, key));
      if (size != null) {
        return size;
      }
      size = getSizeImpl(key);
      if (size != null) {
        return size;
      }
      return getDefaultSizeOn(screen, key);
    }

    Dimension getDefaultSizeOn(GraphicsDevice screen, @NotNull String key) {
      return null;
    }

    Dimension getSizeImpl(@NotNull String key) {
      synchronized (myStateMap) {
        WindowState state = myStateMap.get(key);
        return state == null ? null : state.getSize();
      }
    }

    @Override
    void putSizeOn(GraphicsDevice screen, @NotNull String key, Dimension size) {
      putOn(screen, key, null, false, size, true, null, false);
    }

    @Override
    Rectangle getBoundsOn(GraphicsDevice screen, @NotNull String key) {
      Rectangle bounds = getBoundsImpl(getKey(screen, key));
      if (bounds != null) {
        return bounds;
      }
      bounds = getBoundsImpl(key);
      if (bounds != null) {
        return bounds;
      }
      return getDefaultBoundsOn(screen, key);
    }

    Rectangle getDefaultBoundsOn(GraphicsDevice screen, @NotNull String key) {
      return null;
    }

    private Rectangle getBoundsImpl(@NotNull String key) {
      synchronized (myStateMap) {
        WindowState state = myStateMap.get(key);
        return state == null || !state.isVisible() ? null : state.getBounds();
      }
    }

    @Override
    void putBoundsOn(GraphicsDevice screen, @NotNull String key, Rectangle bounds) {
      Point location = bounds == null ? null : bounds.getLocation();
      Dimension size = bounds == null ? null : bounds.getSize();
      putOn(screen, key, location, true, size, true, null, false);
    }

    Integer getDefaultExtendedStateOn(GraphicsDevice screen, @NotNull String key) {
      return null;
    }

    @Override
    public final Element getState() {
      Element element = new Element(STATE);
      synchronized (myStateMap) {
        for (Map.Entry<String, WindowState> entry : myStateMap.entrySet()) {
          String key = entry.getKey();
          if (key != null) {
            WindowState state = entry.getValue();
            Element child = new Element(STATE);
            if (state.myLocation != null) {
              writeTo(child, X, state.myLocation.x);
              writeTo(child, Y, state.myLocation.y);
            }
            if (state.mySize != null) {
              writeTo(child, WIDTH, state.mySize.width);
              writeTo(child, HEIGHT, state.mySize.height);
            }
            if (state.myState != null) {
              writeTo(child, EXTENDED, state.myState);
            }
            writeTo(element, KEY, key);
            element.addContent(child);
          }
        }
      }
      return element;
    }

    @Override
    public final void loadState(Element element) {
      synchronized (myStateMap) {
        myStateMap.clear();
        for (Element child : element.getChildren()) {
          if (STATE.equals(child.getName())) {
            String key = child.getAttributeValue(KEY);
            if (key != null) {
              WindowState state = new WindowState();
              state.myLocation = loadLocationFrom(child);
              state.mySize = loadSizeFrom(child);
              state.myState = loadExtendedStateFrom(child);
              if (!state.isEmpty()) {
                myStateMap.put(key, state);
              }
            }
          }
        }
      }
    }
  }

  private static int loadFrom(@NotNull Element element, @NotNull String name) {
    return Integer.parseInt(element.getAttributeValue(name));
  }

  static Point loadLocationFrom(@NotNull Element element) {
    try {
      return new Point(loadFrom(element, X), loadFrom(element, Y));
    }
    catch (NumberFormatException exception) {
      return null;
    }
  }

  static Dimension loadSizeFrom(@NotNull Element element) {
    try {
      return new Dimension(loadFrom(element, WIDTH), loadFrom(element, HEIGHT));
    }
    catch (NumberFormatException exception) {
      return null;
    }
  }

  static Integer loadExtendedStateFrom(@NotNull Element element) {
    try {
      return loadFrom(element, EXTENDED);
    }
    catch (NumberFormatException exception) {
      return null;
    }
  }

  static void writeTo(@NotNull Element element, @NotNull String name, @NotNull Object value) {
    element.setAttribute(name, value.toString());
  }

  @NotNull
  static String getKey(GraphicsDevice screen, String key) {
    GraphicsEnvironment environment = GraphicsEnvironment.getLocalGraphicsEnvironment();
    if (environment.isHeadlessInstance()) {
      return key + ".headless";
    }
    StringBuilder sb = new StringBuilder(key);
    for (GraphicsDevice device : environment.getScreenDevices()) {
      Rectangle bounds = device.getDefaultConfiguration().getBounds();
      sb.append('/').append(bounds.x);
      sb.append('.').append(bounds.y);
      sb.append('.').append(bounds.width);
      sb.append('.').append(bounds.height);
    }
    if (screen != null) {
      Rectangle bounds = screen.getDefaultConfiguration().getBounds();
      sb.append('@').append(bounds.x);
      sb.append('.').append(bounds.y);
      sb.append('.').append(bounds.width);
      sb.append('.').append(bounds.height);
    }
    return sb.toString();
  }
}
