// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui.laf.intellij;

import com.intellij.ide.ui.laf.darcula.ui.DarculaPasswordFieldUI;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import javax.swing.text.JTextComponent;
import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
public class MacIntelliJPasswordFieldUI extends DarculaPasswordFieldUI {

  @SuppressWarnings({"MethodOverridesStaticMethodOfSuperclass", "UnusedDeclaration"})
  public static ComponentUI createUI(JComponent c) {
    return new MacIntelliJPasswordFieldUI();
  }

  @Override
  protected void paintBackground(Graphics graphics) {
    Graphics2D g = (Graphics2D)graphics;
    final JTextComponent c = getComponent();
    final Container parent = c.getParent();
    if (c.isOpaque() && parent != null) {
      g.setColor(parent.getBackground());
      g.fillRect(0, 0, c.getWidth(), c.getHeight());
    }

    if (c.getBorder() instanceof MacIntelliJTextBorder) {
      g.setColor(c.getBackground());
      if (JBUI.isCompensateVisualPaddingOnComponentLevel(parent)) {
        g.fillRect(3, 3, c.getWidth() - 6, c.getHeight() - 6);
      }
      else {
        g.fillRect(5, 5, c.getWidth() - 6 - MacIntelliJTextFieldUI.BW, c.getHeight() - 6 - MacIntelliJTextFieldUI.BW);
      }
    } else {
      super.paintBackground(g);
    }
  }

  @NotNull
  @Override
  public Dimension getPreferredSize(JComponent c) {
    Dimension size = super.getPreferredSize(c);
    if (JBUI.isCompensateVisualPaddingOnComponentLevel(c.getParent())) {
      return new Dimension(size.width, Math.max(26, size.height));
    }
    else {
      return new Dimension(size.width, JBUI.scale(MacIntelliJTextFieldUI.MACOS_LIGHT_INPUT_HEIGHT_TOTAL));
    }
  }

  @Override
  public Dimension getMinimumSize(JComponent c) {
    Dimension size = super.getMinimumSize(c);
    return JBUI.isCompensateVisualPaddingOnComponentLevel(c.getParent()) ? size : new Dimension(size.width, JBUI.scale(MacIntelliJTextFieldUI.MACOS_LIGHT_INPUT_HEIGHT_TOTAL));
  }
}
