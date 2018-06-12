// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui.laf.intellij;

import com.intellij.ide.ui.laf.darcula.ui.DarculaPasswordFieldUI;
import com.intellij.util.ui.JBInsets;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import javax.swing.text.JTextComponent;
import java.awt.*;

import static com.intellij.ide.ui.laf.intellij.MacIntelliJTextBorder.MINIMUM_HEIGHT;

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
      if (c.isEnabled() && c.isEditable()) {
        g.setColor(c.getBackground());

        Rectangle r = new Rectangle(c.getSize());
        JBInsets.removeFrom(r, c.getInsets());

        g.fillRect(r.x, r.y, r.width, r.height);
      }
    } else {
      super.paintBackground(g);
    }
  }

  @Override
  protected int getMinimumHeight() {
    return MINIMUM_HEIGHT.get();
  }
}
