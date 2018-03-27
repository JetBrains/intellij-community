/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.ide.ui.laf.darcula.ui.DarculaRadioButtonUI;
import com.intellij.util.ui.JBUI;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
public class WinIntelliJRadioButtonUI extends DarculaRadioButtonUI {
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
    Icon icon = MacIntelliJIconCache.getIcon("radio", false, bm.isSelected(), focused, bm.isEnabled(), bm.isPressed());

    // Paint the radio button
    int x = (iconRect.width - icon.getIconWidth()) / 2 + iconRect.x;
    int y = (iconRect.height - icon.getIconHeight()) / 2 + iconRect.y + JBUI.scale(1);
    icon.paintIcon(c, g, x, y);
  }
}
