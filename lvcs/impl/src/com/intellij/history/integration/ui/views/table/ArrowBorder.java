package com.intellij.history.integration.ui.views.table;

import com.intellij.util.ui.UIUtil;

import javax.swing.border.Border;
import java.awt.*;

public class ArrowBorder implements Border {
  private int myArrowWidth = 7;
  private Insets myInsets;
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