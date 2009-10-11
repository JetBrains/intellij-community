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

import com.intellij.util.ui.UIUtil;

import java.awt.*;

/**
 * @author kir
 */
public class DrawUtil {
  private DrawUtil() {
  }

  public static void drawRoundRect(Graphics g, double x1d, double y1d, double x2d, double y2d, Color color) {
    final Color oldColor = g.getColor();
    g.setColor(color);

    int x1 = (int) Math.round(x1d);
    int x2 = (int) Math.round(x2d);
    int y1 = (int) Math.round(y1d);
    int y2 = (int) Math.round(y2d);

    UIUtil.drawLine(g, x1 + 1, y1, x2 - 1, y1);
    UIUtil.drawLine(g, x1 + 1, y2, x2 - 1, y2);

    UIUtil.drawLine(g, x1, y1 + 1, x1, y2 - 1);
    UIUtil.drawLine(g, x2, y1 + 1, x2, y2 - 1);

    g.setColor(oldColor);
  }

  public static void drawPlainRect(Graphics g, int x1, int y1, int x2, int y2) {
    UIUtil.drawLine(g, x1, y1, x2 - 1, y1);
    UIUtil.drawLine(g, x2, y1, x2, y2 - 1);
    UIUtil.drawLine(g, x1 + 1, y2, x2, y2);
    UIUtil.drawLine(g, x1, y1 + 1, x1, y2);
  }

}
