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

import com.intellij.ide.ui.laf.darcula.DarculaUIUtil;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicPasswordFieldUI;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.MouseListener;

import static com.intellij.ide.ui.laf.intellij.WinIntelliJTextFieldUI.HOVER_PROPERTY;

public class WinIntelliJPasswordFieldUI extends BasicPasswordFieldUI {

  private final JPasswordField passwordField;
  private MouseListener hoverListener;
  private FocusListener focusListener;

  public WinIntelliJPasswordFieldUI(JPasswordField passwordField) {
    this.passwordField = passwordField;
  }

  @SuppressWarnings({"MethodOverridesStaticMethodOfSuperclass", "UnusedDeclaration"})
  public static ComponentUI createUI(JComponent c) {
    return new WinIntelliJPasswordFieldUI((JPasswordField)c);
  }

  @Override public void installListeners() {
    super.installListeners();
    hoverListener = new DarculaUIUtil.MouseHoverPropertyTrigger(passwordField, HOVER_PROPERTY);
    focusListener = new FocusListener() {
      @Override public void focusGained(FocusEvent e) {
        passwordField.repaint();
      }

      @Override public void focusLost(FocusEvent e) {
        passwordField.repaint();
      }
    };

    passwordField.addMouseListener(hoverListener);
    passwordField.addFocusListener(focusListener);
  }

  @Override public void uninstallListeners() {
    super.uninstallListeners();
    if (hoverListener != null) {
      passwordField.removeMouseListener(hoverListener);
    }

    if (focusListener != null) {
      passwordField.removeFocusListener(focusListener);
    }
  }

  @Override
  protected void paintBackground(Graphics g) {
    JTextComponent c = getComponent();

    Graphics2D g2 = (Graphics2D)g.create();
    try {
      Container parent = c.getParent();
      if (c.isOpaque() && parent != null) {
        g2.setColor(parent.getBackground());
        g2.fillRect(0, 0, c.getWidth(), c.getHeight());
      }

      if (c.getBorder() instanceof WinIntelliJTextBorder){
        WinIntelliJTextFieldUI.paintTextFieldBackground(c, g2);
      }
    } finally {
      g2.dispose();
    }
  }
}
