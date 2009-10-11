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

package com.intellij.history.integration.ui.views.table;

import com.intellij.util.ui.UIUtil;

import javax.swing.border.Border;
import java.awt.*;

public class ArrowBorder implements Border {
  private final int myArrowWidth = 7;
  private final Insets myInsets;
  private Color myColor;

  public ArrowBorder() {
    myInsets = new Insets(1, myArrowWidth, 1, 0);
  }

  public boolean isBorderOpaque() {
    return true;
  }

  public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
    g.setColor(c.getBackground());
    g.fillRect(0, 0, myArrowWidth, height);

    g.setColor(myColor);

    int horizAxis = y + height / 2;

    UIUtil.drawLine(g, 0, horizAxis, 2, horizAxis);
    int right = x + myArrowWidth;


    int[] xPoints = new int[]{x + 2, right, right};
    int[] yPoints = new int[]{horizAxis, y, y + height};

    g.drawPolygon(xPoints, yPoints, 3);
    g.fillPolygon(xPoints, yPoints, 3);

    g.drawRect(right, y, width - right, height - 1);
  }

  public Insets getBorderInsets(Component c) {
    return myInsets;
  }

  public void setColor(Color c) {
    myColor = c;
  }
}
