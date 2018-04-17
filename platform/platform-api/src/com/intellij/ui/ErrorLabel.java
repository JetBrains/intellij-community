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

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;

/**
 * @author kir
 */
public class ErrorLabel extends JLabel {

  private boolean myUnderline;

  private Color myForeground;
  private String myTooltip;

  public ErrorLabel() {
    this(null, null);
  }

  public ErrorLabel(String text) {
    this(text, null);
  }

  public ErrorLabel(String text, Icon icon) {
    super(text, icon, SwingConstants.LEFT);
    setOpaque(false);
  }

  public void setErrorText(String text, Color color) {
    boolean newUnderline = text != null;
    myForeground = color;
    if (newUnderline) {
      updateLabelView(newUnderline, text);
    }
    else if (myUnderline) {
      updateLabelView(newUnderline, myTooltip);
    }
  }

  @Override
  public void setToolTipText(String text) {
    if (myUnderline) {
      myTooltip = text;
    }
    else {
      super.setToolTipText(text);
    }
  }



  private void updateLabelView(boolean newUnderline, String tooltip) {
    super.setToolTipText(tooltip);
    myUnderline = newUnderline;
    repaint();
  }

  @Override
  protected void paintComponent(Graphics g) {
    super.paintComponent(g);

    String text = getText();
    if (text != null && myUnderline) {
      g.setColor(myForeground);
      int x = 0;

      Icon icon = getIcon();
      if (icon != null) {
        x = icon.getIconWidth() + getIconTextGap();
      }

      if (getHorizontalAlignment() == CENTER) {
        int w = g.getFontMetrics().stringWidth(text);
        x += (getWidth() - x - w) >> 1;
      }

      Border border = getBorder();
      if (border != null) {
        Insets insets = border.getBorderInsets(this);
        if (insets.left != -1) {
          x += insets.left;
        }
      }

      drawWave(this, g, x, text);
    }
  }

  public static void drawWave(Component c, Graphics g, int x, String text) {
    int y = getTextBaseLine(c);

    y += 2;

    int width = c.getFontMetrics(c.getFont()).stringWidth(text);
    int nLines = (width >> 1) + 1;

    int xCurr = x;
    int yBottom = y + 1;
    int []xx = new int[nLines + 1];
    int []yy = new int[nLines + 1];
    int line = 0;
    for (; line < nLines; line += 2) {
      xx[line] = xCurr;
      yy[line] = yBottom;

      xx[line + 1] = xCurr + 2;
      yy[line + 1] = yBottom - 2;
      xCurr += 4;
    }

    g.drawPolyline(xx, yy, line);
  }

  private static int getTextBaseLine(Component c) {
    FontMetrics fm = c.getFontMetrics(c.getFont());
    return (c.getHeight() >> 1) + (fm.getHeight() >> 1) - fm.getDescent();
  }
}
