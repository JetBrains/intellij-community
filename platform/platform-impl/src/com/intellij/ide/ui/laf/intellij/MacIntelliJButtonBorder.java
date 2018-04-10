// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui.laf.intellij;

import com.intellij.ide.ui.laf.VisualPaddingsProvider;
import com.intellij.ide.ui.laf.darcula.DarculaUIUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.plaf.UIResource;
import java.awt.*;

import static com.intellij.ide.ui.laf.intellij.MacIntelliJButtonUI.ARC_SIZE;

/**
 * @author Konstantin Bulenkov
 */
public class MacIntelliJButtonBorder implements Border, UIResource, VisualPaddingsProvider {
  @Override
  public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
    if (!c.hasFocus() || c instanceof JComponent && UIUtil.isHelpButton(c)) return;

    Graphics2D g2 = (Graphics2D)g.create();
    try {
      g2.translate(x, y);
      DarculaUIUtil.paintFocusBorder(g2, width, height, ARC_SIZE, true);
    } finally {
      g2.dispose();
    }
  }

  @Override
  public Insets getBorderInsets(Component c) {
    return JBUI.insets(3).asUIResource();
  }

  @Override
  public boolean isBorderOpaque() {
    return false;
  }

  @Nullable
  @Override
  public Insets getVisualPaddings(@NotNull Component component) {
    return JBUI.insets(3);
  }
}
