// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
public class DarculaCheckBoxBorder implements Border, UIResource {
  @Override
  public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {}

  @Override
  public Insets getBorderInsets(Component c) {
    int bw = UIUtil.getParentOfType(CellRendererPane.class, c) != null ? 0 : JBUI.getInt(borderWidthPropertyName(), 1);
    return JBUI.insets(bw, bw, bw, 0).asUIResource();
  }

  protected String borderWidthPropertyName() {
    return "CheckBox.border.width";
  }

  @Override
  public boolean isBorderOpaque() {
    return false;
  }
}
