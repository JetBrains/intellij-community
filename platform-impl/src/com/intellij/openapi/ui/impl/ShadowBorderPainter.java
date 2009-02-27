package com.intellij.openapi.ui.impl;

import com.intellij.openapi.util.IconLoader;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * @author spleaner
 */
public class ShadowBorderPainter {
  private static final Icon TOP = IconLoader.getIcon("/ide/shadow/top.png");
  private static final Icon TOP_RIGHT = IconLoader.getIcon("/ide/shadow/top-right.png");
  private static final Icon RIGHT = IconLoader.getIcon("/ide/shadow/right.png");
  private static final Icon BOTTOM_RIGHT = IconLoader.getIcon("/ide/shadow/bottom-right.png");
  private static final Icon BOTTOM = IconLoader.getIcon("/ide/shadow/bottom.png");
  private static final Icon BOTTOM_LEFT = IconLoader.getIcon("/ide/shadow/bottom-left.png");
  private static final Icon LEFT = IconLoader.getIcon("/ide/shadow/left.png");
  private static final Icon TOP_LEFT = IconLoader.getIcon("/ide/shadow/top-left.png");

  public static final int SIDE_SIZE = 35;
  public static final int TOP_SIZE = 20;
  public static final int BOTTOM_SIZE = 49;

  private ShadowBorderPainter() {
  }

  public static BufferedImage createShadow(final JComponent c, final int width, final int height) {
    final GraphicsConfiguration graphicsConfiguration = GraphicsEnvironment.getLocalGraphicsEnvironment().
        getDefaultScreenDevice().getDefaultConfiguration();

    final BufferedImage image = graphicsConfiguration.createCompatibleImage(width, height, Transparency.TRANSLUCENT);;
    final Graphics2D g = image.createGraphics();

    TOP_LEFT.paintIcon(c, g, 0, 0);
    TOP_RIGHT.paintIcon(c, g, width - SIDE_SIZE * 2, 0);

    for (int _x = SIDE_SIZE * 2; _x < width - SIDE_SIZE * 2; _x ++) {
      TOP.paintIcon(c, g, _x, 0);
    }

    for (int _x = BOTTOM_SIZE * 2; _x < width - BOTTOM_SIZE * 2; _x ++) {
      BOTTOM.paintIcon(c, g, _x, height - BOTTOM_SIZE);
    }

    for (int _y = SIDE_SIZE * 2; _y < height - BOTTOM_SIZE * 2; _y ++) {
      LEFT.paintIcon(c, g, 0, _y);
      RIGHT.paintIcon(c, g, width - SIDE_SIZE, _y);
    }

    BOTTOM_RIGHT.paintIcon(c, g, width - BOTTOM_SIZE * 2, height - BOTTOM_SIZE * 2);
    BOTTOM_LEFT.paintIcon(c, g, 0, height - BOTTOM_SIZE * 2);

    g.dispose();
    return image;
  }
}
