/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
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
      if (r.x <= aTargetX && aTargetX <= r.x + r.width && r.y <= aTargetY && aTargetY <= r.y + r.height) {
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
}
