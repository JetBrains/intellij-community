// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.laf.darcula.ui;

import com.intellij.ui.ComponentUtil;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;

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
    return ComponentUtil.getParentOfType(CellRendererPane.class, c) != null ?
           JBInsets.emptyInsets().asUIResource() :
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
