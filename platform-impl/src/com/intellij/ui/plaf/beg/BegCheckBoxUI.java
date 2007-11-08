package com.intellij.ui.plaf.beg;

import com.intellij.util.ui.UIUtil;

import java.awt.*;
import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.metal.MetalCheckBoxUI;

public class BegCheckBoxUI extends MetalCheckBoxUI {
  private static final BegCheckBoxUI begCheckBoxUI = new BegCheckBoxUI();

  public static ComponentUI createUI(JComponent c) {
    return begCheckBoxUI;
  }

  protected void paintFocus(Graphics g, Rectangle t, Dimension d) {
    g.setColor(getFocusColor());
    UIUtil.drawDottedRectangle(g, t.x - 2, t.y - 1, t.x + t.width + 1, t.y + t.height);
  }
}