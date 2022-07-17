// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.laf.darcula;

import com.intellij.util.ui.JBInsets;

import javax.swing.border.Border;
import javax.swing.plaf.UIResource;
import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
public class DarculaTableHeaderBorder implements Border, UIResource {

  @Override
  public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
  }

  @Override
  public Insets getBorderInsets(Component c) {
    return JBInsets.emptyInsets();
  }

  @Override
  public boolean isBorderOpaque() {
    return false;
  }
}
