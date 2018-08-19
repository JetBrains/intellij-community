/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;

import javax.swing.border.Border;
import java.awt.*;

public class DottedBorder implements Border {
  private final int myTop, myBottom, myLeft, myRight;
  private final Color myColor;

  public DottedBorder(Insets insets, Color color) {
    myTop = insets.top;
    myBottom = insets.bottom;
    myLeft = insets.left;
    myRight = insets.right;
    myColor = color;
  }

  public DottedBorder(Color color) {
    this(JBUI.insets(1), color);
  }

  @Override
  public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
    g.setColor(myColor);
    UIUtil.drawDottedRectangle(g, x, y, x + width - 1, y + height - 1);
  }

  @Override
  public Insets getBorderInsets(Component c) {
    //return a copy, otherwise someone could change our insets from outside
    return new Insets(myTop, myLeft, myBottom, myRight);
  }

  @Override
  public boolean isBorderOpaque() {
    return true;
  }
}
