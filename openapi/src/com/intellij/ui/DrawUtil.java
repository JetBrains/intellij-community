/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.ui;

import java.awt.*;

/**
 * @author kir
 */
public class DrawUtil {

  public static void drawRoundRect(Graphics g, double x1d, double y1d, double x2d, double y2d, Color color) {
    final Color oldColor = g.getColor();
    g.setColor(color);

    int x1 = (int) Math.round(x1d);
    int x2 = (int) Math.round(x2d);
    int y1 = (int) Math.round(y1d);
    int y2 = (int) Math.round(y2d);

    g.drawLine(x1 + 1, y1, x2 - 1, y1);
    g.drawLine(x1 + 1, y2, x2 - 1, y2);

    g.drawLine(x1, y1 + 1, x1, y2 - 1);
    g.drawLine(x2, y1 + 1, x2, y2 - 1);

    g.setColor(oldColor);
  }

  public static void drawPlainRect(Graphics g, int x1, int y1, int x2, int y2) {
    g.drawLine(x1, y1, x2 - 1, y1);
    g.drawLine(x2, y1, x2, y2 - 1);
    g.drawLine(x1 + 1, y2, x2, y2);
    g.drawLine(x1, y1 + 1, x1, y2);
  }

}
