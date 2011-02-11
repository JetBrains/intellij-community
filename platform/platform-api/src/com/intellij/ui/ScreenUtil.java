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
 */
public class ScreenUtil {

  public static final Rectangle getScreenRectangle(int x, int y) {
    GraphicsConfiguration targetGraphicsConfiguration = null;
    GraphicsEnvironment env = GraphicsEnvironment.getLocalGraphicsEnvironment();
    GraphicsDevice[] devices = env.getScreenDevices();

    int minDistance;
    GraphicsConfiguration minDsitanceConfig;
    for (int i = 0; i < devices.length; i++) {
      GraphicsDevice eachDevice = devices[i];
      GraphicsConfiguration eachConfig = eachDevice.getDefaultConfiguration();
      Rectangle eachRec = eachConfig.getBounds();

      Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(eachConfig);
      if (insets != null) {
        eachRec.x += insets.left;
        eachRec.width -= (insets.left + insets.right);
        eachRec.y += insets.top;
        eachRec.height -= (insets.top + insets.bottom);
      }

      if (eachRec.x <= x && x < eachRec.x + eachRec.width && eachRec.y <= y && y < eachRec.y + eachRec.height) {
        targetGraphicsConfiguration = eachConfig;
        break;
      }


    }
    if (targetGraphicsConfiguration == null && devices.length > 0) {
      targetGraphicsConfiguration = env.getDefaultScreenDevice().getDefaultConfiguration();
    }
    if (targetGraphicsConfiguration == null) {
      throw new IllegalStateException(
          "It's impossible to determine target graphics environment for point (" + x + "," + y + ")"
      );
    }

    // Determine real client area of target graphics configuration

    Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(targetGraphicsConfiguration);
    Rectangle targetRectangle = targetGraphicsConfiguration.getBounds();
    targetRectangle.x += insets.left;
    targetRectangle.y += insets.top;
    targetRectangle.width -= insets.left + insets.right;
    targetRectangle.height -= insets.top + insets.bottom;

    return targetRectangle;
  }


  public static boolean isOutsideOnTheRightOFScreen(Rectangle aRectangle) {
    int screenX = aRectangle.x;
    int screenY = aRectangle.y;
    Rectangle screen = getScreenRectangle(screenX, screenY);
    return aRectangle.getMaxX() > screen.getMaxX();
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

  public static void cropRectangleToFitTheScreen(Rectangle aRectangle) {
    int screenX = aRectangle.x;
    int screenY = aRectangle.y;
    Rectangle screen = getScreenRectangle(screenX, screenY);

    if (aRectangle.getMaxX() > screen.getMaxX()) {
      aRectangle.width = (int) screen.getMaxX() - aRectangle.x;
    }


    if (aRectangle.getMinX() < screen.getMinX()) {
      aRectangle.x = (int) screen.getMinX();
    }

    if (aRectangle.getMaxY() > screen.getMaxY()) {
      aRectangle.height = (int) screen.getMaxY() - aRectangle.y;
    }

    if (aRectangle.getMinY() < screen.getMinY()) {
      aRectangle.y = (int) screen.getMinY();
    }
  }

}
