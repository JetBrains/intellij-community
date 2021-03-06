// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.ui.paint.LinePainter2D;

import java.awt.*;

/**
 * @author kir
 */
public final class DrawUtil {
  private DrawUtil() {
  }

  public static void drawRoundRect(Graphics g, double x1d, double y1d, double x2d, double y2d, Color color) {
    final Color oldColor = g.getColor();
    g.setColor(color);

    int x1 = (int) Math.round(x1d);
    int x2 = (int) Math.round(x2d);
    int y1 = (int) Math.round(y1d);
    int y2 = (int) Math.round(y2d);

    LinePainter2D.paint((Graphics2D)g, x1 + 1, y1, x2 - 1, y1);
    LinePainter2D.paint((Graphics2D)g, x1 + 1, y2, x2 - 1, y2);

    LinePainter2D.paint((Graphics2D)g, x1, y1 + 1, x1, y2 - 1);
    LinePainter2D.paint((Graphics2D)g, x2, y1 + 1, x2, y2 - 1);

    g.setColor(oldColor);
  }

  public static void drawPlainRect(Graphics g, int x1, int y1, int x2, int y2) {
    LinePainter2D.paint((Graphics2D)g, x1, y1, x2 - 1, y1);
    LinePainter2D.paint((Graphics2D)g, x2, y1, x2, y2 - 1);
    LinePainter2D.paint((Graphics2D)g, x1 + 1, y2, x2, y2);
    LinePainter2D.paint((Graphics2D)g, x1, y1 + 1, x1, y2);
  }

}
