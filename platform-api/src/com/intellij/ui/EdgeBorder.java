/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;

public class EdgeBorder implements Border {
  public static final int EDGE_RIGHT = 61440;
  public static final int EDGE_BOTTOM = 3840;
  public static final int EDGE_LEFT = 240;
  public static final int EDGE_TOP = 15;
  public static final int EDGE_ALL = 65535;
  private Insets myInsets;
  private int b;

  public EdgeBorder(int i) {
    myInsets = new Insets(2, 2, 2, 2);
    b = i;
    recalcInsets();
  }

  public boolean isBorderOpaque() {
    return true;
  }

  public Insets getBorderInsets(Component component) {
    return myInsets;
  }

  public void paintBorder(Component component, Graphics g, int x, int y, int width, int height) {
    java.awt.Color color = UIUtil.getSeparatorShadow();
    java.awt.Color color1 = UIUtil.getSeparatorHighlight();
    java.awt.Color color2 = g.getColor();
    if ((b & 0xf) != 0){
      g.setColor(color);
      UIUtil.drawLine(g, x, y, (x + width) - 1, y);
      g.setColor(color1);
      UIUtil.drawLine(g, x, y + 1, (x + width) - 1, y + 1);
    }
    if ((b & 0xf0) != 0){
      g.setColor(color);
      UIUtil.drawLine(g, x, y, x, (y + height) - 1);
      g.setColor(color1);
      UIUtil.drawLine(g, x + 1, y, x + 1, (y + height) - 1);
    }
    if ((b & 0xf00) != 0){
      g.setColor(color);
      UIUtil.drawLine(g, x, (y + height) - 2, (x + width) - 1, (y + height) - 2);
      g.setColor(color1);
      UIUtil.drawLine(g, x, (y + height) - 1, (x + width) - 1, (y + height) - 1);
    }
    if ((b & 0xf000) != 0){
      g.setColor(color1);
      UIUtil.drawLine(g, (x + width) + 1, y, (x + width) + 1, (y + height) - 1);
      g.setColor(color);
      UIUtil.drawLine(g, (x + width), y, (x + width), (y + height) - 1);
    }
    g.setColor(color2);
  }

  protected void recalcInsets() {
    myInsets.top = (b & 0xf) == 0 ? 0 : 2;
    myInsets.left = (b & 0xf0) == 0 ? 0 : 2;
    myInsets.bottom = (b & 0xf00) == 0 ? 0 : 2;
    myInsets.right = (b & 0xf000) == 0 ? 0 : 2;
  }
}
