// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui.laf.intellij;

import com.intellij.ide.ui.laf.darcula.ui.DarculaCheckBoxUI;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.IconCache;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
public class WinIntelliJCheckBoxUI extends DarculaCheckBoxUI {
  private static final Icon DEFAULT_ICON = JBUI.scale(EmptyIcon.create(13)).asUIResource();

  @Nullable
  @Override
  public Insets getVisualPaddings(@NotNull Component component) {
    return JBUI.insets(1, 0);
  }

  @SuppressWarnings({"MethodOverridesStaticMethodOfSuperclass", "UnusedDeclaration"})
  public static ComponentUI createUI(JComponent c) {
    AbstractButton b = (AbstractButton)c;
    b.setRolloverEnabled(true);
    return new WinIntelliJCheckBoxUI();
  }

  @Override
  protected void drawCheckIcon(JComponent c, Graphics2D g, AbstractButton b, Rectangle iconRect, boolean selected, boolean enabled) {
    ButtonModel bm = b.getModel();
    boolean focused = c.hasFocus() || bm.isRollover() || isCellRollover(b);
    boolean pressed = bm.isPressed() || isCellPressed(b);

    String iconName = isIndeterminate(b) ? "checkBoxIndeterminate" : "checkBox";
    Icon icon = IconCache.getIcon(iconName, selected || isIndeterminate(b), focused, enabled, false, pressed);

    if (icon != null) {
      icon.paintIcon(c, g, iconRect.x, iconRect.y);
    }
  }

  private static boolean isCellRollover(AbstractButton checkBox) {
    Rectangle cellPosition = (Rectangle)checkBox.getClientProperty(UIUtil.CHECKBOX_ROLLOVER_PROPERTY);
    return cellPosition != null && cellPosition.getBounds().equals(checkBox.getBounds());
  }

  private static boolean isCellPressed(AbstractButton checkBox) {
    Rectangle cellPosition = (Rectangle)checkBox.getClientProperty(UIUtil.CHECKBOX_PRESSED_PROPERTY);
    return cellPosition != null && cellPosition.getBounds().equals(checkBox.getBounds());
  }

  @Override
  public Icon getDefaultIcon() {
    return DEFAULT_ICON;
  }

  @Override
  protected int textIconGap() {
    return JBUI.scale(4);
  }
}
