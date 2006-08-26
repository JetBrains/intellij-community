/*
 * Copyright 2000-2005 JetBrains s.r.o.
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

import java.awt.*;

/**
 * @author kir
 */
public class ScreenUtil {

  public static final Rectangle getScreenRectangle(int aTargetX, int aTargetY) {
    GraphicsConfiguration targetGraphicsConfiguration = null;
    GraphicsEnvironment env = GraphicsEnvironment.getLocalGraphicsEnvironment();
    GraphicsDevice[] devices = env.getScreenDevices();
    for (int i = 0; i < devices.length; i++) {
      GraphicsDevice device = devices[i];
      GraphicsConfiguration graphicsConfiguration = device.getDefaultConfiguration();
      Rectangle r = graphicsConfiguration.getBounds();
      if (r.x <= aTargetX && aTargetX < r.x + r.width && r.y <= aTargetY && aTargetY < r.y + r.height) {
        targetGraphicsConfiguration = graphicsConfiguration;
        break;
      }
    }
    if (targetGraphicsConfiguration == null && devices.length > 0) {
      targetGraphicsConfiguration = env.getDefaultScreenDevice().getDefaultConfiguration();
    }
    if (targetGraphicsConfiguration == null) {
      throw new IllegalStateException(
          "It's impossible to determine target graphics environment for point (" + aTargetX + "," + aTargetY + ")"
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
    return aRectangle.getMaxX() > screen.width;
  }

  public static void moveRectangleToFitTheScreen(Rectangle aRectangle) {
    int screenX = aRectangle.x;
    int screenY = aRectangle.y;
    Rectangle screen = getScreenRectangle(screenX, screenY);

    if (aRectangle.getMaxX() > screen.getMaxX()) {
      aRectangle.x = (int) screen.getMaxX() - aRectangle.width;
    }


    if (aRectangle.getMinX() < screen.getMinX()) {
      aRectangle.x = (int) screen.getMinX();
    }

    if (aRectangle.getMaxY() > screen.getMaxY()) {
      aRectangle.y = (int) screen.getMaxY() - aRectangle.height;
    }

    if (aRectangle.getMinY() < screen.getMinY()) {
      aRectangle.y = (int) screen.getMinY();
    }
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
