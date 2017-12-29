/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ide.ui.laf.intellij;

import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
public class WinIntelliJCheckBoxUI extends IntelliJCheckBoxUI {

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
    Icon icon = MacIntelliJIconCache.getIcon(iconName, false, selected || isIndeterminate(b), focused, enabled, pressed);

    if (icon != null) {
      int x = (iconRect.width - icon.getIconWidth()) / 2 + iconRect.x;
      int y = (iconRect.height - icon.getIconHeight()) / 2 + iconRect.y + JBUI.scale(1);
      icon.paintIcon(c, g, x, y);
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
    return JBUI.scale(EmptyIcon.create(18)).asUIResource();
  }

  @Override
  protected boolean fillBackgroundForIndeterminateSameAsForSelected() {
    return false;
  }
}
