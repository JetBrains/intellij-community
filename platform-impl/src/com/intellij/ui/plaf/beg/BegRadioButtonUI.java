package com.intellij.ui.plaf.beg;

import com.intellij.util.ui.UIUtil;

import java.awt.*;
import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.metal.MetalRadioButtonUI;

public class BegRadioButtonUI extends MetalRadioButtonUI {
  private static final BegRadioButtonUI begRadioButtonUI = new BegRadioButtonUI();

  public static ComponentUI createUI(JComponent c) {
    return begRadioButtonUI;
  }

  protected void paintFocus(Graphics g, Rectangle t, Dimension d) {
    g.setColor(getFocusColor());
    UIUtil.drawDottedRectangle(g, t.x - 2, t.y - 1, t.x + t.width + 1, t.y + t.height);
  }
}