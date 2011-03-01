/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
 * @author kir
 * @author Konstantin Bulenkov
 */
public class ScreenUtil {
  private ScreenUtil() {
  }

  public static Rectangle getScreenRectangle(Point p) {
    GraphicsConfiguration targetGC = null;
    final GraphicsEnvironment env = GraphicsEnvironment.getLocalGraphicsEnvironment();
    final GraphicsDevice[] devices = env.getScreenDevices();
    double distance = -1;
    GraphicsConfiguration bestConfig = null;

    for (GraphicsDevice device: devices) {
      final GraphicsConfiguration config = device.getDefaultConfiguration();
      final Rectangle rect = config.getBounds();

      final Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(config);
      if (insets != null) {
        rect.x += insets.left;
        rect.width -= (insets.left + insets.right);
        rect.y += insets.top;
        rect.height -= (insets.top + insets.bottom);
      }

      if (rect.contains(p)) {
        targetGC = config;
        break;
      } else {
        final double d = findNearestPointOnBorder(rect, p).distance(p.x, p.y);
        if (bestConfig == null || distance > d) {
          distance = d;
          bestConfig = config;
        }
      }
    }

    if (targetGC == null && devices.length > 0 && bestConfig != null) {
      targetGC = bestConfig;
      //targetGC = env.getDefaultScreenDevice().getDefaultConfiguration();
    }
    if (targetGC == null) {
      throw new IllegalStateException("It's impossible to determine target graphics environment for point (" + p.x + "," + p.y + ")");
    }

    //Determine real client area of target graphics configuration
    final Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(targetGC);
    Rectangle targetRect = targetGC.getBounds();
    targetRect.x += insets.left;
    targetRect.y += insets.top;
    targetRect.width -= insets.left + insets.right;
    targetRect.height -= insets.top + insets.bottom;

    return targetRect;
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

    Rectangle move = new Rectangle(rectangle.x - insets.left, rectangle.y - insets.top, rectangle.width + insets.left + insets.right, rectangle.height + insets.top + insets.bottom);

    if (move.getMaxX() > container.getMaxX()) {
      move.x = (int) container.getMaxX() - move.width;
    }


    if (move.getMinX() < container.getMinX()) {
      move.x = (int) container.getMinX();
    }

    if (move.getMaxY() > container.getMaxY()) {
      move.y = (int) container.getMaxY() - move.height;
    }

    if (move.getMinY() < container.getMinY()) {
      move.y = (int) container.getMinY();
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
      rect.width = (int) screen.getMaxX() - rect.x;
    }

    if (rect.getMinX() < screen.getMinX()) {
      rect.x = (int) screen.getMinX();
    }

    if (rect.getMaxY() > screen.getMaxY()) {
      rect.height = (int) screen.getMaxY() - rect.y;
    }

    if (rect.getMinY() < screen.getMinY()) {
      rect.y = (int) screen.getMinY();
    }
  }
}
