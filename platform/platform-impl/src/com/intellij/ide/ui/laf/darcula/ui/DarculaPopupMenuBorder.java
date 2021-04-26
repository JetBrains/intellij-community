// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui.laf.darcula.ui;

import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;

import javax.swing.border.AbstractBorder;
import javax.swing.plaf.UIResource;
import javax.swing.plaf.basic.BasicComboPopup;
import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
public class DarculaPopupMenuBorder extends AbstractBorder implements UIResource {
  private static final JBInsets DEFAULT_INSETS = JBUI.insets(1);

  @Override
  public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
  }

  @Override
  public Insets getBorderInsets(Component c) {
    if (isComboPopup(c)) {
      return JBInsets.create(1, 2).asUIResource();
    }
    else {
      return JBUI.insets("PopupMenu.borderInsets", DEFAULT_INSETS).asUIResource();
    }
  }

  protected static boolean isComboPopup(Component c) {
    return "ComboPopup.popup".equals(c.getName()) && c instanceof BasicComboPopup;
  }
}
