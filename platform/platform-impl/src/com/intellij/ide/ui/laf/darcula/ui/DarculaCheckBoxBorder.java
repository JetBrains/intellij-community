// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui.laf.darcula.ui;

import com.intellij.ide.ui.laf.VisualPaddingsProvider;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.plaf.ButtonUI;
import javax.swing.plaf.UIResource;
import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
public class DarculaCheckBoxBorder implements Border, UIResource, VisualPaddingsProvider {
  @Override
  public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {}

  @Override
  public Insets getBorderInsets(Component c) {
    return JBUI.insets(UIUtil.getParentOfType(CellRendererPane.class, c) != null ? 0 : 2, 0).asUIResource();
  }

  @Override
  public boolean isBorderOpaque() {
    return false;
  }

  @Nullable
  @Override
  public Insets getVisualPaddings(@NotNull Component component) {
    if (component instanceof AbstractButton) {
      ButtonUI ui = ((AbstractButton)component).getUI();
      if (ui instanceof VisualPaddingsProvider) {
        return ((VisualPaddingsProvider)ui).getVisualPaddings(component);
      }
    }

    // unrealistic
    return JBUI.emptyInsets();
  }
}
