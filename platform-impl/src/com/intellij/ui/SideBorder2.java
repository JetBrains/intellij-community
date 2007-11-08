package com.intellij.ui;

import com.intellij.util.ui.UIUtil;

import javax.swing.border.Border;
import java.awt.*;

public class SideBorder2 implements Border {
  public boolean isBorderOpaque() {
    return false;
  }

  private Color myLeftColor;
  private Color myRightColor;
  private Color myTopColor;
  private Color myBottomColor;
  private int myThickness;

  public SideBorder2(Color topColor, Color leftColor, Color bottomColor, Color rightColor, int thickness) {
    myTopColor = topColor;
    myLeftColor = leftColor;
    myRightColor = rightColor;
    myBottomColor = bottomColor;
    myThickness = thickness;
  }

  public Insets getBorderInsets(Component component) {
    return new Insets(
      myTopColor != null ? getThickness() : 0,
      myLeftColor != null ? getThickness() : 0,
      myBottomColor != null ? getThickness() : 0,
      myRightColor != null ? getThickness() : 0
    );
  }

  public Insets getBorderInsets(Component component, Insets insets) {
    insets.top = myTopColor != null ? getThickness() : 0;
    insets.left = myLeftColor != null ? getThickness() : 0;
    insets.bottom = myBottomColor != null ? getThickness() : 0;
    insets.right = myRightColor != null ? getThickness() : 0;
    return insets;
  }

  public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
    Color oldColor = g.getColor();
    int i;

    for(i = 0; i < getThickness(); i++){
      if (myLeftColor != null){
        g.setColor(myLeftColor);
        UIUtil.drawLine(g, x + i, y + i, x + i, height - i - i - 1);
      }
      if (myTopColor != null){
        g.setColor(myTopColor);
        UIUtil.drawLine(g, x + i, y + i, width - i - i - 1, y + i);
      }
      if (myRightColor != null){
        g.setColor(myRightColor);
        UIUtil.drawLine(g, width - i - i - 1, y + i, width - i - i - 1, height - i - i - 1);
      }
      if (myBottomColor != null){
        g.setColor(myBottomColor);
        UIUtil.drawLine(g, x + i, height - i - i - 1, width - i - i - 1, height - i - i - 1);
      }
    }
    g.setColor(oldColor);
  }

  public int getThickness() {
    return myThickness;
  }
}