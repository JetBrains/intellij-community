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
package com.intellij.ide.ui.laf.darcula;

import com.intellij.ui.Gray;
import com.intellij.util.ui.UIUtil;
import sun.swing.SwingUtilities2;

import javax.swing.*;
import javax.swing.plaf.LabelUI;
import javax.swing.plaf.basic.BasicLabelUI;
import java.awt.*;

/**
* @author Konstantin Bulenkov
*/
class DarculaWelcomeScreenLabelUI extends BasicLabelUI {

  @SuppressWarnings({"MethodOverridesStaticMethodOfSuperclass", "UnusedDeclaration"})
  public static LabelUI createUI(JComponent c) {
    return new DarculaWelcomeScreenLabelUI();
  }

  @Override
  public void paint(Graphics g, JComponent c) {
    if(c.isEnabled()) {
      super.paint(g, c);
    }
  }

  @Override
  protected void paintEnabledText(JLabel l, Graphics g, String s, int x, int y) {
    g.setColor(l.getForeground().equals(UIUtil.getPanelBackground()) ? Gray._255.withAlpha(60) : Gray._0.withAlpha(150));
    SwingUtilities2.drawStringUnderlineCharAt(l, g, s, -1, x, y + 1);
    g.setColor(l.getForeground());
    SwingUtilities2.drawStringUnderlineCharAt(l, g, s, -1, x, y);
  }

  @Override
  public Dimension getPreferredSize(JComponent c) {
    final Dimension size = super.getPreferredSize(c);
    return new Dimension(size.width, size.height + 1);
  }
}
