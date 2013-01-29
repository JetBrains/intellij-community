/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.ide.ui.laf.darcula.ui;

import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicButtonUI;
import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
public class DarculaButtonUI extends BasicButtonUI {
  @SuppressWarnings("MethodOverridesStaticMethodOfSuperclass")
  public static ComponentUI createUI(JComponent c) {
    return new DarculaButtonUI();
  }

  @Override
  public void paint(Graphics g, JComponent c) {
    final Border border = c.getBorder();
    if (c.isEnabled() && border != null) {
      final Insets ins = border.getBorderInsets(c);
      final int yOff = (ins.top + ins.bottom) / 4;
      if (((JButton)c).isDefaultButton()) {
        ((Graphics2D)g).setPaint(UIUtil.getGradientPaint(0, 0, new Color(0x384F6B), 0, c.getHeight(), new Color(0x233143)));
      }
      else {
        ((Graphics2D)g).setPaint(UIUtil.getGradientPaint(0, 0, new Color(85, 90, 92), 0, c.getHeight(), new Color(65, 70, 72)));
      }
      g.fillRoundRect(4, yOff, c.getWidth() - 2 * 4, c.getHeight() - 2 * yOff, 5, 5);
    }
    super.paint(g, c);
  }
}
