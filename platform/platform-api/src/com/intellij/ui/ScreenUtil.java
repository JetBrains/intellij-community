/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.containers.WeakHashMap;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.Map;

/**
 * @author kir
 * @author Konstantin Bulenkov
 */
public class ScreenUtil {
  @Nullable private static final Map<GraphicsConfiguration, Pair<Insets, Long>> ourInsetsCache;
  static {
    final boolean useCache = SystemInfo.isXWindow && !GraphicsEnvironment.isHeadless();
    ourInsetsCache = useCache ? new WeakHashMap<GraphicsConfiguration, Pair<Insets, Long>>() : null;
  }

  private ScreenUtil() { }

  public static boolean isVisible(Rectangle bounds) {
    final Rectangle intersection = getScreenBounds().intersection(bounds);
    final int sq1 = intersection.width * intersection.height;
    final int sq2 = bounds.width * bounds.height;
    if (sq1 == 0 || sq2 == 0) return false;
    return (double)sq1 / (double)sq2 > 0.1;
  }

  public static Rectangle getScreenBounds() {
    Rectangle screenBounds = new Rectangle();
    final GraphicsEnvironment env = GraphicsEnvironment.getLocalGraphicsEnvironment();
    final GraphicsDevice[] devices = env.getScreenDevices();
    for (final GraphicsDevice device : devices) {
      screenBounds = screenBounds.union(device.getDefaultConfiguration().getBounds());
    }
    return screenBounds;
  }

  public static Rectangle getScreenRectangle(Point p) {
    double distance = -1;
    Rectangle answer = null;

    for (GraphicsDevice device : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
      GraphicsConfiguration config = device.getDefaultConfiguration();
      final Rectangle rect = applyInsets(config.getBounds(), getScreenInsets(config));
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

  private static Rectangle applyInsets(Rectangle rect, Insets i) {
    if (i == null) {
      return rect;
    }

    return new Rectangle(rect.x + i.left, rect.y + i.top, rect.width - (i.left + i.right), rect.height - (i.top + i.bottom));
  }

  private static Insets fixGlobalInsets(Rectangle rect, Insets i) {
    if (i == null) {
      //noinspection ConstantConditions
      return i;
    }
    int top = i.top > rect.y ? i.top - rect.y : i.top;
    int left = i.left > rect.x ? i.left - rect.x : i.left;
    return new Insets(top, left, i.bottom, i.right);
  }

  public static Insets getScreenInsets(final GraphicsConfiguration gc) {
    if (ourInsetsCache == null) {
      return calcInsets(gc);
    }

    synchronized (ourInsetsCache) {
      Pair<Insets, Long> data = ourInsetsCache.get(gc);
      final long now = System.currentTimeMillis();
      if (data == null || now > data.second + /*17 * */1000) {  // keep for 17s
        data = Pair.create(calcInsets(gc), now);
        ourInsetsCache.put(gc, data);
      }
      return data.first;
    }
  }

  private static Insets calcInsets(GraphicsConfiguration gc) {
    Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(gc);
    if (SystemInfo.isLinux && GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices().length > 1) {
      return fixGlobalInsets(gc.getBounds(), insets);
    }
    return insets;
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
