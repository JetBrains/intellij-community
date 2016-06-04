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

import com.intellij.ide.ui.laf.darcula.ui.DarculaTextFieldUI;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.plaf.ComponentUI;
import javax.swing.text.JTextComponent;
import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
public class WinIntelliJTextFieldUI extends DarculaTextFieldUI {
  public WinIntelliJTextFieldUI(JTextField textField) {
    super(textField);
  }

  @SuppressWarnings({"MethodOverridesStaticMethodOfSuperclass", "UnusedDeclaration"})
  public static ComponentUI createUI(JComponent c) {
    return new WinIntelliJTextFieldUI((JTextField)c);
  }

  @Override
  protected void paintBackground(Graphics graphics) {
    super.paintBackground(graphics);
  }

  @Override
  protected void paintDarculaBackground(Graphics2D g, JTextComponent c, Border border) {
    super.paintDarculaBackground(g, c, border);
  }

  @Override
  protected void paintSearchField(Graphics2D g, JTextComponent c, Rectangle r) {
    super.paintSearchField(g, c, r);
  }
}
