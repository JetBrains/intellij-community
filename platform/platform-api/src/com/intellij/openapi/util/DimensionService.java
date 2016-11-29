/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.ui.ScreenUtil;
import com.intellij.util.containers.hash.LinkedHashMap;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import gnu.trove.TObjectIntHashMap;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Map;

/**
 * This class represents map between strings and rectangles. It's intended to store
 * sizes of window, dialogs, etc.
 */
@State(
  name = "DimensionService",
  storages = {
    @Storage(value = "dimensions.xml", roamingType = RoamingType.DISABLED),
    @Storage(value = "options.xml", deprecated = true)
  }
)
public class DimensionService implements PersistentStateComponent<Element> {
  private static final Logger LOG = Logger.getInstance(DimensionService.class);

  private final Map<String, Point> myKey2Location;
  private final Map<String, Dimension> myKey2Size;
  private final TObjectIntHashMap<String> myKey2ExtendedState;
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
    return ServiceManager.getService(DimensionService.class);
  }

  /**
   * Invoked by reflection
   */
  private DimensionService() {
    myKey2Location = new LinkedHashMap<>();
    myKey2Size = new LinkedHashMap<>();
    myKey2ExtendedState = new TObjectIntHashMap<>();
  }

  /**
   * @param key a String key to perform a query for.
   * @return point stored under the specified <code>key</code>. The method returns
   * <code>null</code> if there is no stored value under the <code>key</code>. If point
   * is outside of current screen bounds then the method returns <code>null</code>. It
   * properly works in multi-monitor configuration.
   * @throws java.lang.IllegalArgumentException if <code>key</code> is <code>null</code>.
   */
  @Nullable
  public synchronized Point getLocation(String key) {
    return getLocation(key, guessProject());
  }

  @Nullable
  public synchronized Point getLocation(@NotNull String key, Project project) {
    Point point = myKey2Location.get(realKey(key, project));
    if (point != null && !ScreenUtil.getScreenRectangle(point).contains(point)) {
      point = null;
    }
    return point != null ? (Point)point.clone() : null;
  }

  /**
   * Store specified <code>point</code> under the <code>key</code>. If <code>point</code> is
   * <code>null</code> then the value stored under <code>key</code> will be removed.
   *
   * @param key   a String key to store location for.
   * @param point location to save.
   * @throws java.lang.IllegalArgumentException if <code>key</code> is <code>null</code>.
   */
  public synchronized void setLocation(String key, Point point) {
    setLocation(key, point, guessProject());
  }

  public synchronized void setLocation(@NotNull String key, Point point, Project project) {
    key = realKey(key, project);

    if (point != null) {
      myKey2Location.put(key, (Point)point.clone());
    }
    else {
      myKey2Location.remove(key);
    }
  }

  /**
   * @param key a String key to perform a query for.
   * @return point stored under the specified <code>key</code>. The method returns
   * <code>null</code> if there is no stored value under the <code>key</code>.
   * @throws java.lang.IllegalArgumentException if <code>key</code> is <code>null</code>.
   */
  @Nullable
  public synchronized Dimension getSize(@NotNull @NonNls String key) {
    return getSize(key, guessProject());
  }

  @Nullable
  public synchronized Dimension getSize(@NotNull @NonNls String key, Project project) {
    Dimension size = myKey2Size.get(realKey(key, project));
    return size != null ? (Dimension)size.clone() : null;
  }

  /**
   * Store specified <code>size</code> under the <code>key</code>. If <code>size</code> is
   * <code>null</code> then the value stored under <code>key</code> will be removed.
   *
   * @param key  a String key to to save size for.
   * @param size a Size to save.
   * @throws java.lang.IllegalArgumentException if <code>key</code> is <code>null</code>.
   */
  public synchronized void setSize(@NotNull @NonNls String key, Dimension size) {
    setSize(key, size, guessProject());
  }

  public synchronized void setSize(@NotNull @NonNls String key, Dimension size, Project project) {
    key = realKey(key, project);

    if (size != null) {
      myKey2Size.put(key, (Dimension)size.clone());
    }
    else {
      myKey2Size.remove(key);
    }
  }

  @Override
  public Element getState() {
    Element element = new Element("state");
    // Save locations
    for (String key : myKey2Location.keySet()) {
      Point point = myKey2Location.get(key);
      LOG.assertTrue(point != null);
      Element e = new Element(ELEMENT_LOCATION);
      e.setAttribute(KEY, key);
      e.setAttribute(ATTRIBUTE_X, String.valueOf(point.x));
      e.setAttribute(ATTRIBUTE_Y, String.valueOf(point.y));
      element.addContent(e);
    }

    // Save sizes
    for (String key : myKey2Size.keySet()) {
      Dimension size = myKey2Size.get(key);
      LOG.assertTrue(size != null);
      Element e = new Element(ELEMENT_SIZE);
      e.setAttribute(KEY, key);
      e.setAttribute(ATTRIBUTE_WIDTH, String.valueOf(size.width));
      e.setAttribute(ATTRIBUTE_HEIGHT, String.valueOf(size.height));
      element.addContent(e);
    }

    // Save extended states
    for (Object stateKey : myKey2ExtendedState.keys()) {
      String key = (String)stateKey;
      Element e = new Element(EXTENDED_STATE);
      e.setAttribute(KEY, key);
      e.setAttribute(STATE, String.valueOf(myKey2ExtendedState.get(key)));
      element.addContent(e);
    }
    return element;
  }

  @Override
  public void loadState(final Element element) {
    myKey2Location.clear();
    myKey2Size.clear();
    myKey2ExtendedState.clear();

    for (Element e : element.getChildren()) {
      if (ELEMENT_LOCATION.equals(e.getName())) {
        try {
          myKey2Location.put(e.getAttributeValue(KEY), new Point(Integer.parseInt(e.getAttributeValue(ATTRIBUTE_X)),
                                                                 Integer.parseInt(e.getAttributeValue(ATTRIBUTE_Y))));
        }
        catch (NumberFormatException ignored) {
        }
      }
      else if (ELEMENT_SIZE.equals(e.getName())) {
        try {
          myKey2Size.put(e.getAttributeValue(KEY), new Dimension(Integer.parseInt(e.getAttributeValue(ATTRIBUTE_WIDTH)),
                                                                 Integer.parseInt(e.getAttributeValue(ATTRIBUTE_HEIGHT))));
        }
        catch (NumberFormatException ignored) {
        }
      }
      else if (EXTENDED_STATE.equals(e.getName())) {
        try {
          myKey2ExtendedState.put(e.getAttributeValue(KEY), Integer.parseInt(e.getAttributeValue(STATE)));
        }
        catch (NumberFormatException ignored) {
        }
      }
    }
  }

  /**
   * @deprecated Use {@link com.intellij.ide.util.PropertiesComponent}
   */
  @Deprecated
  public void setExtendedState(String key, int extendedState) {
    myKey2ExtendedState.put(key, extendedState);
  }

  /**
   * @deprecated Use {@link com.intellij.ide.util.PropertiesComponent}
   */
  @Deprecated
  public int getExtendedState(String key) {
    if (!myKey2ExtendedState.containsKey(key)) return -1;
    return myKey2ExtendedState.get(key);
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
    if (frame != null) {
      final Point topLeft = frame.getLocation();
      Point center = new Point(topLeft.x + frame.getWidth() / 2, topLeft.y + frame.getHeight() / 2);
      for (GraphicsDevice device : env.getScreenDevices()) {
        Rectangle bounds = device.getDefaultConfiguration().getBounds();
        if (bounds.contains(center)) {
          screen = bounds;
          break;
        }
      }
    }
    else {
      GraphicsConfiguration gc = env.getScreenDevices()[0].getDefaultConfiguration();
      screen = gc.getBounds();
    }
    String realKey = key + '.' + screen.x + '.' + screen.y + '.' + screen.width + '.' + screen.height;
    if (JBUI.isHiDPI()) {
      realKey+= "@" + (((int)(96 * JBUI.scale(1f)))) + "dpi";
    }
    return realKey;
  }
}
