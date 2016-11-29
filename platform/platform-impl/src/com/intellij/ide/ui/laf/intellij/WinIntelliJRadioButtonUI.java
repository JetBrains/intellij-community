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
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
public class WinIntelliJRadioButtonUI extends DarculaRadioButtonUI {
  @SuppressWarnings({"MethodOverridesStaticMethodOfSuperclass", "UnusedDeclaration"})
  public static ComponentUI createUI(JComponent c) {
    return new WinIntelliJRadioButtonUI();
  }

  @Override
  protected void paintIcon(JComponent c, Graphics2D g, Rectangle viewRect, Rectangle iconRect) {
    final boolean selected = ((AbstractButton)c).isSelected();
    final boolean enabled = c.isEnabled();
    final Icon icon = MacIntelliJIconCache.getIcon("radio", selected, false, enabled);
    // Paint the radio button
    final int x = (iconRect.width - icon.getIconWidth()) / 2;
    final int y = (viewRect.height - icon.getIconHeight()) / 2;
    icon.paintIcon(c, g, x, y);
  }

  @Override
  protected void paintFocus(Graphics g, Rectangle t, Dimension d) {
    g.setColor(getFocusColor());
    t.x -= 2; t.y -=1;
    t.width += 3; t.height +=2;
    UIUtil.drawDottedRectangle(g, t);
  }
}
