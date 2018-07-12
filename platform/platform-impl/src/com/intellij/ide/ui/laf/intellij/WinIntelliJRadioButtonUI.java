// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui.laf.intellij;

import com.intellij.ide.ui.laf.darcula.ui.DarculaRadioButtonUI;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.LafIconLookup;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
public class WinIntelliJRadioButtonUI extends DarculaRadioButtonUI {
  private static final Icon DEFAULT_ICON = JBUI.scale(EmptyIcon.create(13)).asUIResource();

  @Override
  protected Rectangle updateViewRect(AbstractButton b, Rectangle viewRect) {
    JBInsets.removeFrom(viewRect, b.getInsets());
    return viewRect;
  }

  @Override
  protected Dimension updatePreferredSize(JComponent c, Dimension size) {
    return size;
  }

  @SuppressWarnings({"MethodOverridesStaticMethodOfSuperclass", "UnusedDeclaration"})
  public static ComponentUI createUI(JComponent c) {
    AbstractButton b = (AbstractButton)c;
    b.setRolloverEnabled(true);
    return new WinIntelliJRadioButtonUI();
  }

  @Override
  protected void paintIcon(JComponent c, Graphics2D g, Rectangle viewRect, Rectangle iconRect) {
    AbstractButton b = (AbstractButton)c;
    ButtonModel bm = b.getModel();
    boolean focused = c.hasFocus() || bm.isRollover();
    Icon icon = LafIconLookup.getIcon("radio", bm.isSelected(), focused, bm.isEnabled(), false, bm.isPressed());
    icon.paintIcon(c, g, iconRect.x, iconRect.y);
  }

  @Override
  public Icon getDefaultIcon() {
    return DEFAULT_ICON;
  }
}
