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
    return UIUtil.getParentOfType(CellRendererPane.class, c) != null ?
           JBUI.emptyInsets().asUIResource() :
           JBUI.insets(borderWidthPropertyName(), JBUI.insets(1));
  }

  protected String borderWidthPropertyName() {
    return "CheckBox.borderInsets";
  }

  @Override
  public boolean isBorderOpaque() {
    return false;
  }
}
