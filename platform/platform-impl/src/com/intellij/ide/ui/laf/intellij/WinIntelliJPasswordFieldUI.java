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
import com.intellij.ide.ui.laf.darcula.ui.DarculaPasswordFieldUI;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.MouseListener;

import static com.intellij.ide.ui.laf.intellij.WinIntelliJTextBorder.MINIMUM_HEIGHT;
import static com.intellij.ide.ui.laf.intellij.WinIntelliJTextFieldUI.HOVER_PROPERTY;

public class WinIntelliJPasswordFieldUI extends DarculaPasswordFieldUI {
  private MouseListener hoverListener;

  @SuppressWarnings({"MethodOverridesStaticMethodOfSuperclass", "UnusedDeclaration"})
  public static ComponentUI createUI(JComponent c) {
    return new WinIntelliJPasswordFieldUI();
  }

  @Override public void installListeners() {
    super.installListeners();
    JTextComponent passwordField = getComponent();
    hoverListener = new DarculaUIUtil.MouseHoverPropertyTrigger(passwordField, HOVER_PROPERTY);
    passwordField.addMouseListener(hoverListener);
  }

  @Override public void uninstallListeners() {
    super.uninstallListeners();
    JTextComponent passwordField = getComponent();
    if (hoverListener != null) {
      passwordField.removeMouseListener(hoverListener);
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

  @Override
  protected int getMinimumHeight() {
    return MINIMUM_HEIGHT.get();
  }
}
