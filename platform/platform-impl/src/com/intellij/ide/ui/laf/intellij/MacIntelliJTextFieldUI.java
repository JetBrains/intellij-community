/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.ide.ui.laf.darcula.ui.DarculaTextFieldUI;
import com.intellij.ui.Gray;
import com.intellij.util.ui.JBUI;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
public class MacIntelliJTextFieldUI extends DarculaTextFieldUI {
  public MacIntelliJTextFieldUI(JTextField textField) {
    super(textField);
  }

  @SuppressWarnings({"MethodOverridesStaticMethodOfSuperclass", "UnusedDeclaration"})
  public static ComponentUI createUI(JComponent c) {
    return new MacIntelliJTextFieldUI(((JTextField)c));
  }


  @Override
  public Dimension getPreferredSize(JComponent c) {
    return JBUI.size(super.getPreferredSize(c).width, 23);
  }

  @Override
  protected void paintBackground(Graphics g) {
    JTextField c = myTextField;
    int w = c.getWidth();
    int h = c.getHeight();
    if (c.hasFocus()) {
      MacIntelliJBorderPainter.paintBorder(c, g, 0, 0, w, h);
    }
    g.setColor(Gray.xBF);
    g.drawRect(2,2,w-4,h-4);
    g.setColor(c.getBackground());
    g.fillRect(3,3,w-6,h-6);
  }

  @Override
  protected void paintSafely(Graphics g) {
    super.paintSafely(g);
  }
}
