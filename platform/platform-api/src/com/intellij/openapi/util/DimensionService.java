// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.ui.ComponentUtil;
import com.intellij.ui.JreHiDpiUtil;
import com.intellij.ui.ScreenUtil;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.containers.ObjectIntHashMap;
import com.intellij.util.containers.ObjectIntMap;
import com.intellij.util.ui.JBUI;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * This class represents map between strings and rectangles. It's intended to store
 * sizes of window, dialogs, etc.
 */
@State(name = "DimensionService", storages = @Storage(value = "window.state.xml", roamingType = RoamingType.DISABLED))
@Service(Service.Level.APP)
public final class DimensionService extends SimpleModificationTracker implements PersistentStateComponent<Element> {
  private static final Logger LOG = Logger.getInstance(DimensionService.class);

  private final Map<String, Point> myKeyToLocation = new LinkedHashMap<>();
  private final Map<String, Dimension> myKeToSize = new LinkedHashMap<>();
  private final ObjectIntMap<String> myKeyToExtendedState = new ObjectIntHashMap<>();
  @NonNls private static final String EXTENDED_STATE = "extendedState";
  @NonNls private static final String KEY = "key";
  @NonNls private static final String STATE = "state";
  @NonNls private static final String ELEMENT_LOCATION = "location";
  @NonNls private static final String ELEMENT_SIZE = "size";
  @NonNls private static final String ATTRIBUTE_X = "x";
  @NonNls private static final String ATTRIBUTE_Y = "y";
  @NonNls private static final String ATTRIBUTE_WIDTH = "width";
  @NonNls private static final String ATTRIBUTE_HEIGHT = "height";

  public static DimensionService getInstance() {
    return ApplicationManager.getApplication().getService(DimensionService.class);
  }

  /**
   * @param key a String key to perform a query for.
   * @return point stored under the specified {@code key}. The method returns
   * {@code null} if there is no stored value under the {@code key}. If point
   * is outside of current screen bounds then the method returns {@code null}. It
   * properly works in multi-monitor configuration.
   * @throws IllegalArgumentException if {@code key} is {@code null}.
   *
   * @deprecated use {@link #getLocation(String, Project)} instead.
   */
  @Deprecated(forRemoval = true)
  @Nullable
  public synchronized Point getLocation(String key) {
    return getLocation(key, guessProject());
  }

  @Nullable
  public synchronized Point getLocation(@NotNull String key, @Nullable Project project) {
    Point point = project == null ? null : WindowStateService.getInstance(project).getLocation(key);
    if (point != null) return point;

    Pair<String, Float> pair = keyPair(key, project);
    point = myKeyToLocation.get(pair.first);
    if (point != null) {
      point = (Point)point.clone();
      float scale = pair.second;
      point.setLocation(point.x / scale, point.y / scale);
    }
    if (point != null && !ScreenUtil.getScreenRectangle(point).contains(point)) {
      point = null;
    }
    return point;
  }

  /**
   * Store specified {@code point} under the {@code key}. If {@code point} is
   * {@code null} then the value stored under {@code key} will be removed.
   *
   * @param key   a String key to store location for.
   * @param point location to save.
   * @throws IllegalArgumentException if {@code key} is {@code null}.
   *
   * @deprecated use {@link #setLocation(String, Point, Project)} instead.
   */
  @Deprecated(forRemoval = true)
  public synchronized void setLocation(String key, Point point) {
    setLocation(key, point, guessProject());
  }

  public synchronized void setLocation(@NotNull String key, Point point, @Nullable Project project) {
    getWindowStateService(project).putLocation(key, point);
    Pair<String, Float> pair = keyPair(key, project);
    if (point != null) {
      point = (Point)point.clone();
      float scale = pair.second;
      point.setLocation(point.x * scale, point.y * scale);
      myKeyToLocation.put(pair.first, point);
    }
    else {
      myKeyToLocation.remove(key);
    }
    incModificationCount();
  }

  /**
   * @param key a String key to perform a query for.
   * @return point stored under the specified {@code key}. The method returns
   * {@code null} if there is no stored value under the {@code key}.
   * @throws IllegalArgumentException if {@code key} is {@code null}.
   *
   * @deprecated use {@link #getSize(String, Project)} instead.
   */
  @Deprecated(forRemoval = true)
  @Nullable
  public synchronized Dimension getSize(@NotNull @NonNls String key) {
    return getSize(key, guessProject());
  }

  @Nullable
  public synchronized Dimension getSize(@NotNull @NonNls String key, @Nullable Project project) {
    Dimension size = project == null ? null : WindowStateService.getInstance(project).getSize(key);
    if (size != null) return size;

    Pair<String, Float> pair = keyPair(key, project);
    size = myKeToSize.get(pair.first);
    if (size != null) {
      size = (Dimension)size.clone();
      float scale = pair.second;
      size.setSize(size.width / scale, size.height / scale);
    }
    return size;
  }

  /**
   * Store specified {@code size} under the {@code key}. If {@code size} is
   * {@code null} then the value stored under {@code key} will be removed.
   *
   * @param key  a String key to to save size for.
   * @param size a Size to save.
   * @throws IllegalArgumentException if {@code key} is {@code null}.
   *
   * @deprecated use {@link #setSize(String, Dimension, Project)} instead.
   */
  @Deprecated(forRemoval = true)
  public synchronized void setSize(@NotNull @NonNls String key, Dimension size) {
    setSize(key, size, guessProject());
  }

  public synchronized void setSize(@NotNull @NonNls String key, Dimension size, @Nullable Project project) {
    getWindowStateService(project).putSize(key, size);
    Pair<String, Float> pair = keyPair(key, project);
    if (size != null) {
      size = (Dimension)size.clone();
      float scale = pair.second;
      size.setSize(size.width * scale, size.height * scale);
      myKeToSize.put(pair.first, size);
    }
    else {
      myKeToSize.remove(pair.first);
    }
    incModificationCount();
  }

  @Override
  public Element getState() {
    Element element = new Element("state");
    // Save locations
    for (Map.Entry<String, Point> entry : myKeyToLocation.entrySet()) {
      Point point = entry.getValue();
      LOG.assertTrue(point != null);
      Element e = new Element(ELEMENT_LOCATION);
      e.setAttribute(KEY, entry.getKey());
      e.setAttribute(ATTRIBUTE_X, String.valueOf(point.x));
      e.setAttribute(ATTRIBUTE_Y, String.valueOf(point.y));
      element.addContent(e);
    }

    // Save sizes
    for (Map.Entry<String, Dimension> entry : myKeToSize.entrySet()) {
      Dimension size = entry.getValue();
      LOG.assertTrue(size != null);
      Element e = new Element(ELEMENT_SIZE);
      e.setAttribute(KEY, entry.getKey());
      e.setAttribute(ATTRIBUTE_WIDTH, String.valueOf(size.width));
      e.setAttribute(ATTRIBUTE_HEIGHT, String.valueOf(size.height));
      element.addContent(e);
    }

    // save extended states

    for (ObjectIntMap.Entry<String> entry : myKeyToExtendedState.entries()) {
      Element e = new Element(EXTENDED_STATE);
      e.setAttribute(KEY, entry.getKey());
      e.setAttribute(STATE, Integer.toString(entry.getValue()));
      element.addContent(e);
    }
    return element;
  }

  @Override
  public void loadState(@NotNull Element element) {
    myKeyToLocation.clear();
    myKeToSize.clear();
    myKeyToExtendedState.clear();

    for (Element e : element.getChildren()) {
      if (ELEMENT_LOCATION.equals(e.getName())) {
        try {
          //noinspection ConstantConditions
          myKeyToLocation.put(e.getAttributeValue(KEY), new Point(Integer.parseInt(e.getAttributeValue(ATTRIBUTE_X)),
                                                                  Integer.parseInt(e.getAttributeValue(ATTRIBUTE_Y))));
        }
        catch (NumberFormatException ignored) {
        }
      }
      else if (ELEMENT_SIZE.equals(e.getName())) {
        try {
          //noinspection ConstantConditions
          myKeToSize.put(e.getAttributeValue(KEY), new Dimension(Integer.parseInt(e.getAttributeValue(ATTRIBUTE_WIDTH)),
                                                                 Integer.parseInt(e.getAttributeValue(ATTRIBUTE_HEIGHT))));
        }
        catch (NumberFormatException ignored) {
        }
      }
      else if (EXTENDED_STATE.equals(e.getName())) {
        try {
          //noinspection ConstantConditions
          myKeyToExtendedState.put(e.getAttributeValue(KEY), Integer.parseInt(e.getAttributeValue(STATE)));
        }
        catch (NumberFormatException ignored) {
        }
      }
    }
  }

  @Deprecated
  @Nullable
  private static Project guessProject() {
    Project[] openProjects = ProjectManager.getInstance().getOpenProjects();
    return openProjects.length == 1 ? openProjects[0] : null;
  }

  /**
   * @return Pair(key, scale) where:
   * key is the HiDPI-aware key,
   * scale is the HiDPI-aware factor to transform size metrics.
   */
  @NotNull
  private static Pair<String, Float> keyPair(String key, @Nullable Project project) {
    GraphicsEnvironment env = GraphicsEnvironment.getLocalGraphicsEnvironment();
    if (env.isHeadlessInstance()) {
      return new Pair<>(key + ".headless", 1f);
    }
    JFrame frame = null;
    final Component owner = IdeFocusManager.findInstance().getFocusOwner();
    if (owner != null) {
      frame = ComponentUtil.getParentOfType((Class<? extends JFrame>)JFrame.class, owner);
    }
    if (frame == null) {
      frame = WindowManager.getInstance().findVisibleFrame();
    }
    if (project != null && (frame == null || (frame instanceof IdeFrame && project != ((IdeFrame)frame).getProject()))) {
      frame = WindowManager.getInstance().getFrame(project);
    }
    Rectangle screen = new Rectangle(0, 0, 0, 0);
    GraphicsDevice gd = null;
    if (frame != null) {
      Point topLeft = frame.getLocation();
      float centerX = topLeft.x + frame.getWidth() / 2f;
      float centerY = topLeft.y + frame.getHeight() / 2f;
      for (GraphicsDevice device : env.getScreenDevices()) {
        Rectangle bounds = device.getDefaultConfiguration().getBounds();
        if (bounds.contains(centerX, centerY)) {
          screen = bounds;
          gd = device;
          break;
        }
      }
    }
    if (gd == null) {
      gd = env.getDefaultScreenDevice();
      screen = gd.getDefaultConfiguration().getBounds();
    }
    float scale = 1f;
    if (JreHiDpiUtil.isJreHiDPIEnabled()) {
      scale = JBUIScale.sysScale(gd.getDefaultConfiguration());
      // normalize screen bounds
      screen.setBounds((int)Math.floor(screen.x * scale), (int)Math.floor(screen.y * scale),
                       (int)Math.ceil(screen.width * scale), (int)Math.ceil(screen.height * scale));
    }
    String realKey = key + '.' + screen.x + '.' + screen.y + '.' + screen.width + '.' + screen.height;
    if (JBUI.isPixHiDPI(gd.getDefaultConfiguration())) {
      int dpi = ((int)(96 * JBUI.pixScale(gd.getDefaultConfiguration())));
      realKey += "@" + dpi + "dpi";
    }
    return new Pair<>(realKey, scale);
  }

  @NotNull
  private static WindowStateService getWindowStateService(@Nullable Project project) {
    return project == null ? WindowStateService.getInstance() : WindowStateService.getInstance(project);
  }
}
