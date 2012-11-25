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

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicSpinnerUI;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;

/**
 * @author Konstantin Bulenkov
 */
public class DarculaSpinnerUI extends BasicSpinnerUI {
  @SuppressWarnings({"MethodOverridesStaticMethodOfSuperclass", "UnusedDeclaration"})
  public static ComponentUI createUI(JComponent c) {
    return new DarculaSpinnerUI();
  }

  @Override
  protected void replaceEditor(JComponent oldEditor, JComponent newEditor) {
    super.replaceEditor(oldEditor, newEditor);
    if (newEditor != null) {
      newEditor.getComponents()[0].addFocusListener(new FocusAdapter() {
        @Override
        public void focusGained(FocusEvent e) {
          spinner.repaint();
        }

        @Override
        public void focusLost(FocusEvent e) {
          spinner.repaint();
        }
      });
    }
  }

  @Override
  public void paint(Graphics g, JComponent c) {
  }

  @Override
  protected Component createPreviousButton() {
    final JComponent button = (JComponent)super.createPreviousButton();
    button.setBorder(new EmptyBorder(1,1,1,1));
    return button;
  }

  @Override
  protected Component createNextButton() {
    final JComponent button = (JComponent)super.createNextButton();
    button.setBorder(new EmptyBorder(1,1,1,1));
    return button;
  }
}
