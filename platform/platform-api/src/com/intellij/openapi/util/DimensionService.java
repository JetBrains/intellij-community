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
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.ui.ScreenUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * This class represents map between strings and rectangles. It's intended to store
 * sizes of window, dialogs, etc.
 */
@State(
  name = "DimensionService",
  storages = {
    @Storage(file = StoragePathMacros.APP_CONFIG + "/dimensions.xml", roamingType = RoamingType.DISABLED),
    @Storage(file = StoragePathMacros.APP_CONFIG + "/options.xml", deprecated = true)
  }
)
public class DimensionService implements PersistentStateComponent<Element> {
  private final Service myService = new Service();
  @NonNls private static final String EXTENDED_STATE = "extendedState";
  @NonNls private static final String STATE = "state";
  @NonNls private static final String ELEMENT_LOCATION = "location";
  @NonNls private static final String ELEMENT_SIZE = "size";

  public static DimensionService getInstance() {
    return ServiceManager.getService(DimensionService.class);
  }

  /**
   * Invoked by reflection
   */
  private DimensionService() {
  }

  /**
   * @param key a String key to perform a query for.
   * @return point stored under the specified <code>key</code>. The method returns
   * <code>null</code> if there is no stored value under the <code>key</code>. If point
   * is outside of current screen bounds then the method returns <code>null</code>. It
   * properly works in multi-monitor configuration.
   */
  @Nullable
  public synchronized Point getLocation(String key) {
    if (!Registry.is("ide.dimension.service.old")) {
      Point location = getLocationOn(null, key);
      if (location != null) {
        return location;
      }
    }
    return myService.getLocationImpl(realKey(key, guessProject()));
  }

  /**
   * @param key     a string to perform a query for
   * @param project a project to find a screen where is the main frame located
   * @return a location stored for the specified {@code key},
   * or {@code null} if there is no stored value
   */
  @Nullable
  public synchronized Point getLocation(@NotNull String key, Project project) {
    if (!Registry.is("ide.dimension.service.old")) {
      Point location = getLocationOn(getDevice(project), key);
      if (location != null) {
        return location;
      }
    }
    return myService.getLocationImpl(realKey(key, project));
  }

  /**
   * @param screen a screen to which a location belongs
   * @param key    a string to perform a query for
   * @return a location stored for the specified {@code key},
   * or {@code null} if there is no stored value
   */
  public synchronized Point getLocationOn(GraphicsDevice screen, String key) {
    if (key != null) {
      Point location = myService.getLocationImpl(getKey(screen, key));
      return location != null ? location : myService.getLocationImpl(key);
    }
    return null;
  }

  /**
   * Store specified <code>point</code> under the <code>key</code>. If <code>point</code> is
   * <code>null</code> then the value stored under <code>key</code> will be removed.
   *
   * @param key   a String key to store location for.
   * @param point location to save.
   */
  public synchronized void setLocation(String key, Point point) {
    if (!Registry.is("ide.dimension.service.old")) {
      setLocationOn(null, key, point);
    }
    else {
      myService.putLocationImpl(realKey(key, guessProject()), point);
    }
  }

  /**
   * Stores the specified {@code point} for the given {@code key} in the specified {@code project}.
   * If {@code point} is {@code null} then the corresponding value will be removed.
   *
   * @param key     a string to store a location for
   * @param point   a location to store
   * @param project a project to find a screen where is the main frame located
   */
  public synchronized void setLocation(@NotNull String key, Point point, Project project) {
    if (!Registry.is("ide.dimension.service.old")) {
      setLocationOn(getDevice(project), key, point);
    }
    else {
      myService.putLocationImpl(realKey(key, project), point);
    }
  }

  /**
   * Stores the specified {@code location} for the given {@code key} on the specified {@code screen}.
   * If {@code location} is {@code null} then the corresponding value will be removed.
   *
   * @param screen   a screen to which a location belongs
   * @param key      a string to store a location for
   * @param location a location to store
   */
  public synchronized void setLocationOn(GraphicsDevice screen, String key, Point location) {
    if (key != null) {
      myService.putLocationImpl(getKey(screen, key), location);
      myService.putLocationImpl(key, location);
    }
  }

  /**
   * @param key a String key to perform a query for.
   * @return point stored under the specified <code>key</code>. The method returns
   * <code>null</code> if there is no stored value under the <code>key</code>.
   */
  @Nullable
  public synchronized Dimension getSize(@NotNull @NonNls String key) {
    if (!Registry.is("ide.dimension.service.old")) {
      Dimension size = getSizeOn(null, key);
      if (size != null) {
        return size;
      }
    }
    return myService.getSizeImpl(realKey(key, guessProject()));
  }

  /**
   * @param key     a string to perform a query for
   * @param project a project to find a screen where is the main frame located
   * @return a size stored for the specified {@code key},
   * or {@code null} if there is no stored value
   */
  @Nullable
  public synchronized Dimension getSize(@NotNull @NonNls String key, Project project) {
    if (!Registry.is("ide.dimension.service.old")) {
      Dimension size = getSizeOn(getDevice(project), key);
      if (size != null) {
        return size;
      }
    }
    return myService.getSizeImpl(realKey(key, project));
  }

  /**
   * @param screen a screen to which a size belongs
   * @param key    a string to perform a query for
   * @return a size stored for the specified {@code key},
   * or {@code null} if there is no stored value
   */
  public synchronized Dimension getSizeOn(GraphicsDevice screen, String key) {
    if (key != null) {
      Dimension size = myService.getSizeImpl(getKey(screen, key));
      return size != null ? size : myService.getSizeImpl(key);
    }
    return null;
  }

  /**
   * Store specified <code>size</code> under the <code>key</code>. If <code>size</code> is
   * <code>null</code> then the value stored under <code>key</code> will be removed.
   *
   * @param key  a String key to to save size for.
   * @param size a Size to save.
   */
  public synchronized void setSize(@NotNull @NonNls String key, Dimension size) {
    if (!Registry.is("ide.dimension.service.old")) {
      setSizeOn(null, key, size);
    }
    else {
      myService.putSizeImpl(realKey(key, guessProject()), size);
    }
  }

  /**
   * Stores the specified {@code size} for the given {@code key} in the specified {@code project}.
   * If {@code size} is {@code null} then the corresponding value will be removed.
   *
   * @param key     a string to store a size for
   * @param size    a size to store
   * @param project a project to find a screen where is the main frame located
   */
  public synchronized void setSize(@NotNull @NonNls String key, Dimension size, Project project) {
    if (!Registry.is("ide.dimension.service.old")) {
      setSizeOn(getDevice(project), key, size);
    }
    else {
      myService.putSizeImpl(realKey(key, project), size);
    }
  }

  /**
   * Stores the specified {@code size} for the given {@code key} on the specified {@code screen}.
   * If {@code size} is {@code null} then the corresponding value will be removed.
   *
   * @param screen a screen to which a size belongs
   * @param key    a string to store a size for
   * @param size   a size to store
   */
  public synchronized void setSizeOn(GraphicsDevice screen, String key, Dimension size) {
    if (key != null) {
      myService.putSizeImpl(getKey(screen, key), size);
      myService.putSizeImpl(key, size);
    }
  }

  @Override
  public Element getState() {
    return myService.getState();
  }

  @Override
  public void loadState(final Element element) {
    myService.loadState(element);
  }

  public void setExtendedState(String key, int extendedState) {
    if (!Registry.is("ide.dimension.service.old")) {
      String newKey = getKey(null, key);
      myService.putExtendedStateImpl(newKey, extendedState);
    }
    myService.putExtendedStateImpl(key, extendedState);
  }

  /**
   * @param key a string to perform a query for
   * @return an extended state stored for the specified {@code key},
   * or {@code null} if there is no stored integer value
   */
  public int getExtendedState(String key) {
    if (!Registry.is("ide.dimension.service.old")) {
      String newKey = getKey(null, key);
      Integer value = myService.getExtendedStateImpl(newKey);
      if (value != null) {
        return value;
      }
    }
    Integer value = myService.getExtendedStateImpl(key);
    return value != null ? value : -1;
  }

  @Nullable
  private static Project guessProject() {
    final Project[] openProjects = ProjectManager.getInstance().getOpenProjects();
    return openProjects.length == 1 ? openProjects[0] : null;
  }

  @NotNull
  private static String realKey(String key, @Nullable Project project) {
    GraphicsEnvironment env = GraphicsEnvironment.getLocalGraphicsEnvironment();
    if (env.isHeadlessInstance()) {
      return key + ".headless";
    }

    JFrame frame = null;
    final Component owner = IdeFocusManager.findInstance().getFocusOwner();
    if (owner != null) {
      frame = UIUtil.getParentOfType(JFrame.class, owner);
    }
    if (frame == null) {
      frame = WindowManager.getInstance().findVisibleFrame();
    }
    if (project != null && (frame == null || (frame instanceof IdeFrame && project != ((IdeFrame)frame).getProject()))) {
      frame = WindowManager.getInstance().getFrame(project);
    }
    Rectangle screen = new Rectangle(0, 0, 0, 0);
    GraphicsDevice device = null;
    if (frame != null) {
      device = ScreenUtil.getScreenDevice(frame.getBounds());
    }
    if (device == null) {
      device = env.getDefaultScreenDevice();
    }
    if (device != null) {
      screen = device.getDefaultConfiguration().getBounds();
    }
    String realKey = key + '.' + screen.x + '.' + screen.y + '.' + screen.width + '.' + screen.height;
    if (JBUI.isHiDPI()) {
      realKey += "@" + JBUI.scale(1) + "x";
    }
    return realKey;
  }

  private static GraphicsDevice getDevice(Project project) {
    if (project != null) {
      JFrame frame = WindowManager.getInstance().getFrame(project);
      if (frame != null) {
        return ScreenUtil.getScreenDevice(frame.getBounds());
      }
    }
    return null;
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

  private static final class Service extends WindowStateService.Service {
    @Override
    void addTo(Element element, String key, Point location, Dimension size, Integer state) {
      if (location != null) {
        Element child = new Element(ELEMENT_LOCATION);
        writeKeyTo(child, key);
        writeLocationTo(child, location);
        element.addContent(child);
      }
      if (size != null) {
        Element child = new Element(ELEMENT_SIZE);
        writeKeyTo(child, key);
        writeSizeTo(child, size);
        element.addContent(child);
      }
      if (state != null) {
        Element child = new Element(EXTENDED_STATE);
        writeKeyTo(child, key);
        writeTo(child, STATE, state);
        element.addContent(child);
      }
    }

    @Override
    void put(String key, Element content) {
      if (ELEMENT_LOCATION.equals(content.getName())) {
        putLocationImpl(key, loadLocationFrom(content));
      }
      else if (ELEMENT_SIZE.equals(content.getName())) {
        putSizeImpl(key, loadSizeFrom(content));
      }
      else if (EXTENDED_STATE.equals(content.getName())) {
        try {
          putExtendedStateImpl(key, Integer.parseInt(content.getAttributeValue(STATE)));
        }
        catch (NumberFormatException ignored) {
        }
      }
    }
  }
}
