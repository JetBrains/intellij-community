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
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicComboBoxUI;
import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
public class DarculaComboBoxUI extends BasicComboBoxUI {
  private final JComboBox myComboBox;

  public DarculaComboBoxUI(JComboBox c) {
    myComboBox = c;
    c.setBorder(null);
  }

  @SuppressWarnings({"MethodOverridesStaticMethodOfSuperclass", "UnusedDeclaration"})
  public static ComponentUI createUI(JComponent c) {
    return new DarculaComboBoxUI(((JComboBox)c));
  }

  @Override
  protected LayoutManager createLayoutManager() {
    return new DarculaComboBoxLayoutManager();
  }

  @Override
  public void paintCurrentValueBackground(Graphics g, Rectangle bounds, boolean hasFocus) {
    super.paintCurrentValueBackground(g, bounds, false);
  }

  @Override
  public void paint(Graphics g, JComponent c) {
    super.paint(g, c);
  }

  @Override
  protected JButton createArrowButton() {
    return new DarculaComboBoxButton(myComboBox);
  }

  class DarculaComboBoxLayoutManager extends BasicComboBoxUI.ComboBoxLayoutManager {
      public void layoutContainer(final Container parent) {
        if (arrowButton != null && !comboBox.isEditable()) {
          final Insets insets = comboBox.getInsets();
          final int width = comboBox.getWidth();
          final int height = comboBox.getHeight();
          arrowButton.setBounds(insets.left, insets.top, width - (insets.left + insets.right), height - (insets.top + insets.bottom));
          return;
        }

        final JComboBox cb = (JComboBox)parent;
        final int width = cb.getWidth();
        final int height = cb.getHeight();

        final Insets insets = getInsets();
        final int buttonHeight = height - (insets.top + insets.bottom);
        final int buttonWidth = 20;

        if (arrowButton != null) {
          arrowButton.setBounds(width - (insets.right + buttonWidth), insets.top, buttonWidth, buttonHeight);
        }

        if (editor != null) {
          final Rectangle editorRect = rectangleForCurrentValue();
          editorRect.width += 4;
          editor.setBounds(editorRect);
        }
      }
  }
}
