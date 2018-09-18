// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui.laf.darcula.ui;

import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;

import javax.swing.border.Border;
import javax.swing.plaf.UIResource;
import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
public class DarculaMenuBarBorder implements Border, UIResource {
  private Color lineColor = JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground();

  public DarculaMenuBarBorder() {}

  public DarculaMenuBarBorder(Color lineColor) {
    this.lineColor = lineColor;
  }

  @Override
  public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) {
    g.translate(x, y);
    w--;h--;
    g.setColor(lineColor);
    UIUtil.drawLine(g, 0, h, w, h);
    g.translate(-x, -y);
  }

  @Override
  public Insets getBorderInsets(Component c) {
    return JBUI.insetsBottom(2).asUIResource();
  }

  @Override
  public boolean isBorderOpaque() {
    return true;
  }
}
