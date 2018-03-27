// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui.laf.darcula.ui;

import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.plaf.UIResource;
import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
public class DarculaMenuBarBorder implements Border, UIResource {
  @Override
  public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) {
    g.translate(x, y);
    w--;h--;
    g.setColor(UIManager.getColor("MenuBar.darcula.borderColor"));
    UIUtil.drawLine(g, 0, h, w, h);
    h--;
    g.setColor(UIManager.getColor("MenuBar.darcula.borderShadowColor"));
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
