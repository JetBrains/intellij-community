/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.ui;

import com.intellij.Patches;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.containers.WeakHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Map;

/**
 * @author kir
 * @author Konstantin Bulenkov
 */
public class ScreenUtil {
  public static final String DISPOSE_TEMPORARY = "dispose.temporary";

  @Nullable private static final Map<GraphicsConfiguration, Pair<Insets, Long>> ourInsetsCache;
  static {
    final boolean useCache = SystemInfo.isXWindow && !GraphicsEnvironment.isHeadless();
    ourInsetsCache = useCache ? new WeakHashMap<GraphicsConfiguration, Pair<Insets, Long>>() : null;
  }
  private static final int ourInsetsTimeout = 5000;  // shouldn't be too long

  private ScreenUtil() { }

  public static boolean isVisible(@NotNull Rectangle bounds) {
    if (bounds.isEmpty()) return false;
    Rectangle[] allScreenBounds = getAllScreenBounds();
    for (Rectangle screenBounds : allScreenBounds) {
      final Rectangle intersection = screenBounds.intersection(bounds);
      if (intersection.isEmpty()) continue;
      final int sq1 = intersection.width * intersection.height;
      final int sq2 = bounds.width * bounds.height;
      return (double)sq1 / (double)sq2 > 0.1;
    }
    return false;
  }

  public static Rectangle getMainScreenBounds() {
    GraphicsConfiguration graphicsConfiguration =
      GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration();
    Rectangle bounds = graphicsConfiguration.getBounds();
    applyInsets(bounds, getScreenInsets(graphicsConfiguration));
    return bounds;
  }

  private static Rectangle[] getAllScreenBounds() {
    final GraphicsEnvironment env = GraphicsEnvironment.getLocalGraphicsEnvironment();
    final GraphicsDevice[] devices = env.getScreenDevices();
    Rectangle[] result = new Rectangle[devices.length];
    for (int i = 0; i < devices.length; i++) {
      GraphicsDevice device = devices[i];
      GraphicsConfiguration configuration = device.getDefaultConfiguration();
      result[i] = new Rectangle(configuration.getBounds());
      applyInsets(result[i], getScreenInsets(configuration));
    }
    return result;
  }

  public static Rectangle getScreenRectangle(@NotNull Point p) {
    double distance = -1;
    Rectangle answer = null;

    Rectangle[] allScreenBounds = getAllScreenBounds();
    for (Rectangle rect : allScreenBounds) {
      if (rect.contains(p)) {
        return rect;
      }

      final double d = findNearestPointOnBorder(rect, p).distance(p.x, p.y);
      if (answer == null || distance > d) {
        distance = d;
        answer = rect;
      }
    }

    if (answer == null) {
      throw new IllegalStateException("It's impossible to determine target graphics environment for point (" + p.x + "," + p.y + ")");
    }

    return answer;
  }

  public static GraphicsDevice getScreenDevice(Rectangle bounds) {
    GraphicsDevice candidate = null;
    int maxIntersection = 0;

    for (GraphicsDevice device : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
      GraphicsConfiguration config = device.getDefaultConfiguration();
      final Rectangle rect = config.getBounds();
      Rectangle intersection = rect.intersection(bounds);
      if (intersection.isEmpty()) {
        continue;
      }
      if (intersection.width * intersection.height > maxIntersection) {
        maxIntersection = intersection.width * intersection.height;
        candidate = device;
      }
    }

    return candidate;
  }

  /**
   * Method removeNotify (and then addNotify) will be invoked for all components when main frame switches between states "Normal" <-> "FullScreen".
   * In this case we shouldn't call Disposer  in removeNotify and/or release some resources that we won't initialize again in addNotify (e.g. listeners).
   */
  public static boolean isStandardAddRemoveNotify(Component component) {
    JRootPane rootPane = findMainRootPane(component);
    return rootPane == null || rootPane.getClientProperty(DISPOSE_TEMPORARY) == null;
  }

  private static JRootPane findMainRootPane(Component component) {
    while (component != null) {
      Container parent = component.getParent();
      if (parent == null) {
        return component instanceof RootPaneContainer ? ((RootPaneContainer)component).getRootPane() : null;
      }
      component = parent;
    }
    return null;
  }

  private static Rectangle applyInsets(Rectangle rect, Insets i) {
    if (i == null) {
      return rect;
    }

    return new Rectangle(rect.x + i.left, rect.y + i.top, rect.width - (i.left + i.right), rect.height - (i.top + i.bottom));
  }

  public static Insets getScreenInsets(final GraphicsConfiguration gc) {
    if (ourInsetsCache == null) {
      return calcInsets(gc);
    }

    synchronized (ourInsetsCache) {
      Pair<Insets, Long> data = ourInsetsCache.get(gc);
      final long now = System.currentTimeMillis();
      if (data == null || now > data.second + ourInsetsTimeout) {
        data = Pair.create(calcInsets(gc), now);
        ourInsetsCache.put(gc, data);
      }
      return data.first;
    }
  }

  private static Insets calcInsets(GraphicsConfiguration gc) {
    if (Patches.SUN_BUG_ID_9000030 && GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices().length > 1) {
      return new Insets(0, 0, 0, 0);
    }

    return Toolkit.getDefaultToolkit().getScreenInsets(gc);
  }

  public static Rectangle getScreenRectangle(int x, int y) {
    return getScreenRectangle(new Point(x, y));
  }

  public static boolean isOutsideOnTheRightOFScreen(Rectangle rect) {
    final int screenX = rect.x;
    final int screenY = rect.y;
    Rectangle screen = getScreenRectangle(screenX, screenY);
    return rect.getMaxX() > screen.getMaxX();
  }

  public static void moveRectangleToFitTheScreen(Rectangle aRectangle) {
    int screenX = aRectangle.x + aRectangle.width / 2;
    int screenY = aRectangle.y + aRectangle.height / 2;
    Rectangle screen = getScreenRectangle(screenX, screenY);

    moveToFit(aRectangle, screen, null);
  }

  public static void moveToFit(final Rectangle rectangle, final Rectangle container, @Nullable Insets padding) {
    Insets insets = padding != null ? padding : new Insets(0, 0, 0, 0);

    Rectangle move = new Rectangle(rectangle.x - insets.left, rectangle.y - insets.top, rectangle.width + insets.left + insets.right,
                                   rectangle.height + insets.top + insets.bottom);

    if (move.getMaxX() > container.getMaxX()) {
      move.x = (int)container.getMaxX() - move.width;
    }


    if (move.getMinX() < container.getMinX()) {
      move.x = (int)container.getMinX();
    }

    if (move.getMaxY() > container.getMaxY()) {
      move.y = (int)container.getMaxY() - move.height;
    }

    if (move.getMinY() < container.getMinY()) {
      move.y = (int)container.getMinY();
    }

    rectangle.x = move.x + insets.left;
    rectangle.y = move.y + insets.right;
    rectangle.width = move.width - insets.left - insets.right;
    rectangle.height = move.height - insets.top - insets.bottom;
  }

  public static void fitToScreen(Rectangle r) {
    Rectangle screen = getScreenRectangle(r.x, r.y);

    int xOverdraft = r.x + r.width - screen.x - screen.width;
    if (xOverdraft > 0) {
      int shift = Math.min(xOverdraft, r.x - screen.x);
      xOverdraft -= shift;
      r.x -= shift;
      if (xOverdraft > 0) {
        r.width -= xOverdraft;
      }
    }

    int yOverdraft = r.y + r.height - screen.y - screen.height;
    if (yOverdraft > 0) {
      int shift = Math.min(yOverdraft, r.y - screen.y);
      yOverdraft -= shift;
      r.y -= shift;
      if (yOverdraft > 0) {
        r.height -= yOverdraft;
      }
    }
  }

  public static Point findNearestPointOnBorder(Rectangle rect, Point p) {
    final int x0 = rect.x;
    final int y0 = rect.y;
    final int x1 = x0 + rect.width;
    final int y1 = y0 + rect.height;
    double distance = -1;
    Point best = null;
    final Point[] variants = {new Point(p.x, y0), new Point(p.x, y1), new Point(x0, p.y), new Point(x1, p.y)};
    for (Point variant : variants) {
      final double d = variant.distance(p.x, p.y);
      if (best == null || distance > d) {
        best = variant;
        distance = d;
      }
    }
    assert best != null;
    return best;
  }

  public static void cropRectangleToFitTheScreen(Rectangle rect) {
    int screenX = rect.x;
    int screenY = rect.y;
    final Rectangle screen = getScreenRectangle(screenX, screenY);

    if (rect.getMaxX() > screen.getMaxX()) {
      rect.width = (int)screen.getMaxX() - rect.x;
    }

    if (rect.getMinX() < screen.getMinX()) {
      rect.x = (int)screen.getMinX();
    }

    if (rect.getMaxY() > screen.getMaxY()) {
      rect.height = (int)screen.getMaxY() - rect.y;
    }

    if (rect.getMinY() < screen.getMinY()) {
      rect.y = (int)screen.getMinY();
    }
  }

  /**
   *
   * @param prevLocation - previous location on screen
   * @param location - current location on screen
   * @param bounds - area to check if location shifted towards or not. Also in screen coordinates
   * @return true if movement from prevLocation to location is towards specified rectangular area
   */
  public static boolean isMovementTowards(final Point prevLocation, final Point location, final Rectangle bounds) {
    if (bounds == null) {
      return false;
    }
    if (prevLocation == null || prevLocation.equals(location)) {
      return true;
    }

    int dx = prevLocation.x - location.x;
    int dy = prevLocation.y - location.y;

    // Check if the mouse goes out of the control.
    if (dx > 0 && bounds.x >= prevLocation.x) return false;
    if (dx < 0 && bounds.x + bounds.width <= prevLocation.x) return false;
    if (dy > 0 && bounds.y + bounds.height >= prevLocation.y) return false;
    if (dy < 0 && bounds.y <= prevLocation.y) return false;
    if (dx == 0) {
      return (location.x >= bounds.x && location.x < bounds.x + bounds.width)
             && (dy > 0 ^ bounds.y > location.y);
    }
    if (dy == 0) {
      return (location.y >= bounds.y && location.y < bounds.y + bounds.height)
             && (dx > 0 ^ bounds.x > location.x);
    }


    // Calculate line equation parameters - y = a * x + b
    float a = (float)dy / dx;
    float b = location.y - a * location.x;

    // Check if crossing point with any tooltip border line is within bounds. Don't bother with floating point inaccuracy here.

    // Left border.
    float crossY = a * bounds.x + b;
    if (crossY >= bounds.y && crossY < bounds.y + bounds.height) return true;

    // Right border.
    crossY = a * (bounds.x + bounds.width) + b;
    if (crossY >= bounds.y && crossY < bounds.y + bounds.height) return true;

    // Top border.
    float crossX = (bounds.y - b) / a;
    if (crossX >= bounds.x && crossX < bounds.x + bounds.width) return true;

    // Bottom border
    crossX = (bounds.y + bounds.height - b) / a;
    if (crossX >= bounds.x && crossX < bounds.x + bounds.width) return true;

    return false;
  }
}
