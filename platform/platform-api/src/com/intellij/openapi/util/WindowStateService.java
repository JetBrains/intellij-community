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

  public static WindowStateService getInstance() {
    return ServiceManager.getService(WindowStateService.class);
  }

  public static WindowStateService getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, WindowStateService.class);
  }

  public final Point getLocation(@NotNull String key) {
    return getLocationOn(null, key);
  }

  abstract Point getLocationOn(GraphicsDevice screen, @NotNull String key);

  public final void putLocation(@NotNull String key, Point location) {
    putLocationOn(null, key, location);
  }

  abstract void putLocationOn(GraphicsDevice screen, @NotNull String key, Point location);

  public final Dimension getSize(@NotNull String key) {
    return getSizeOn(null, key);
  }

  abstract Dimension getSizeOn(GraphicsDevice screen, @NotNull String key);

  public final void putSize(@NotNull String key, Dimension size) {
    putSizeOn(null, key, size);
  }

  abstract void putSizeOn(GraphicsDevice screen, @NotNull String key, Dimension size);

  public final Rectangle getBounds(@NotNull String key) {
    return getBoundsOn(null, key);
  }

  abstract Rectangle getBoundsOn(GraphicsDevice screen, @NotNull String key);

  public final void putBounds(@NotNull String key, Rectangle bounds) {
    putBoundsOn(null, key, bounds);
  }

  abstract void putBoundsOn(GraphicsDevice screen, @NotNull String key, Rectangle bounds);

  public final Integer getExtendedState(@NotNull String key) {
    return getExtendedStateOn(null, key);
  }

  abstract Integer getExtendedStateOn(GraphicsDevice screen, @NotNull String key);

  public final void putExtendedState(@NotNull String key, Integer extendedState) {
    putExtendedStateOn(null, key, extendedState);
  }

  abstract void putExtendedStateOn(GraphicsDevice screen, @NotNull String key, Integer extendedState);

  private static final class WindowState {
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
        return false;
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

    private void putOn(GraphicsDevice screen, @NotNull String key,
                       Point location, boolean locationSet,
                       Dimension size, boolean sizeSet,
                       Integer extendedState, boolean extendedStateSet) {
      synchronized (myStateMap) {
        putImpl(DimensionService.getKey(screen, key), location, locationSet, size, sizeSet, extendedState, extendedStateSet);
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
      Point location = getLocationImpl(DimensionService.getKey(screen, key));
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

    void putLocationImpl(@NotNull String key, Point location) {
      synchronized (myStateMap) {
        putImpl(key, location, true, null, false, null, false);
      }
    }

    @Override
    Dimension getSizeOn(GraphicsDevice screen, @NotNull String key) {
      Dimension size = getSizeImpl(DimensionService.getKey(screen, key));
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

    void putSizeImpl(@NotNull String key, Dimension size) {
      synchronized (myStateMap) {
        putImpl(key, null, false, size, true, null, false);
      }
    }

    @Override
    Rectangle getBoundsOn(GraphicsDevice screen, @NotNull String key) {
      Rectangle bounds = getBoundsImpl(DimensionService.getKey(screen, key));
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

    @Override
    Integer getExtendedStateOn(GraphicsDevice screen, @NotNull String key) {
      Integer extendedState = getExtendedStateImpl(DimensionService.getKey(screen, key));
      if (extendedState != null) {
        return extendedState;
      }
      extendedState = getExtendedStateImpl(key);
      if (extendedState != null) {
        return extendedState;
      }
      return getDefaultExtendedStateOn(screen, key);
    }

    Integer getDefaultExtendedStateOn(GraphicsDevice screen, @NotNull String key) {
      return null;
    }

    Integer getExtendedStateImpl(@NotNull String key) {
      synchronized (myStateMap) {
        WindowState state = myStateMap.get(key);
        return state == null ? null : state.getExtendedState();
      }
    }

    @Override
    void putExtendedStateOn(GraphicsDevice screen, @NotNull String key, Integer extendedState) {
      putOn(screen, key, null, false, null, false, extendedState, true);
    }

    void putExtendedStateImpl(@NotNull String key, Integer extendedState) {
      synchronized (myStateMap) {
        putImpl(key, null, false, null, false, extendedState, true);
      }
    }

    @Override
    public final Element getState() {
      Element element = new Element(STATE);
      synchronized (myStateMap) {
        for (Map.Entry<String, WindowState> entry : myStateMap.entrySet()) {
          String key = entry.getKey();
          if (key != null) {
            WindowState state = entry.getValue();
            addTo(element, key, state.myLocation, state.mySize, state.myState);
          }
        }
      }
      return element;
    }

    void addTo(Element element, String key, Point location, Dimension size, Integer state) {
      Element child = new Element(STATE);
      if (location != null) {
        writeLocationTo(child, location);
      }
      if (size != null) {
        writeSizeTo(child, size);
      }
      if (state != null) {
        writeTo(child, EXTENDED, state);
      }
      writeKeyTo(child, key);
      element.addContent(child);
    }

    @Override
    public final void loadState(Element element) {
      synchronized (myStateMap) {
        myStateMap.clear();
        for (Element child : element.getChildren()) {
          String key = child.getAttributeValue(KEY);
          if (key != null) {
            put(key, child);
          }
        }
      }
    }

    void put(String key, Element content) {
      WindowState state = loadStateFrom(content);
      if (state != null) {
        myStateMap.put(key, state);
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

  private static WindowState loadStateFrom(@NotNull Element element) {
    if (STATE.equals(element.getName())) {
      WindowState state = new WindowState();
      state.myLocation = loadLocationFrom(element);
      state.mySize = loadSizeFrom(element);
      state.myState = loadExtendedStateFrom(element);
      if (!state.isEmpty()) {
        return state;
      }
    }
    return null;
  }

  static void writeTo(@NotNull Element element, @NotNull String name, @NotNull Object value) {
    element.setAttribute(name, value.toString());
  }

  static void writeKeyTo(@NotNull Element element, @NotNull String key) {
    writeTo(element, KEY, key);
  }

  static void writeLocationTo(@NotNull Element element, @NotNull Point location) {
    writeTo(element, X, location.x);
    writeTo(element, Y, location.y);
  }

  static void writeSizeTo(@NotNull Element element, @NotNull Dimension size) {
    writeTo(element, WIDTH, size.width);
    writeTo(element, HEIGHT, size.height);
  }
}
