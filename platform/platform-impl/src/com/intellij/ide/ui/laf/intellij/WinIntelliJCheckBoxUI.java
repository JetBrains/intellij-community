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
    return new WinIntelliJCheckBoxUI();
  }

  @Override
  protected void drawCheckIcon(JComponent c, Graphics2D g, JCheckBox b, Rectangle iconRect, boolean selected, boolean enabled) {
    final Color color = enabled ? b.getForeground() : getBorderColor1(false, false);
    g.setColor(color);
    final Icon icon = MacIntelliJIconCache.getIcon("checkBox", selected, false, enabled);
    Rectangle r = new Rectangle(iconRect.x + JBUI.scale(2), iconRect.y + JBUI.scale(2), iconRect.width - JBUI.scale(4), iconRect.height - JBUI.scale(4));
    icon.paintIcon(c, g, r.x, r.y);
  }

  @Override
  protected void drawText(JComponent c, Graphics2D g, JCheckBox b, FontMetrics fm, Rectangle textRect, String text) {
    super.drawText(c, g, b, fm, textRect, text);
    if (b.hasFocus()) {
      g.setColor(b.getForeground());
      UIUtil.drawDottedRectangle(g, textRect.x - 2, textRect.y - 1, textRect.width + textRect.x + 1, textRect.height + 3);
    }
  }

  @Override
  public Icon getDefaultIcon() {
    return JBUI.scale(EmptyIcon.create(18)).asUIResource();
  }
}
