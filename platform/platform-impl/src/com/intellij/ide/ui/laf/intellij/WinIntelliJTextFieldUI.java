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
import com.intellij.util.ui.JBUI;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * @author Konstantin Bulenkov
 */
public class WinIntelliJTextFieldUI extends DarculaTextFieldUI {
  public static final String HOVER_PROPERTY = "JTextField.hover";

  public WinIntelliJTextFieldUI(JTextField textField) {
    super(textField);
  }

  @SuppressWarnings({"MethodOverridesStaticMethodOfSuperclass", "UnusedDeclaration"})
  public static ComponentUI createUI(JComponent c) {
    c.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseEntered(MouseEvent e) {
        JComponent c = (JComponent)e.getComponent();
        c.putClientProperty(HOVER_PROPERTY, Boolean.TRUE);
        c.repaint();
      }

      @Override
      public void mouseExited(MouseEvent e) {
        JComponent c = (JComponent)e.getComponent();
        c.putClientProperty(HOVER_PROPERTY, Boolean.FALSE);
        c.repaint();
      }
    });

    return new WinIntelliJTextFieldUI((JTextField)c);
  }

  @Override
  protected void paintBackground(Graphics g) {
    Graphics2D g2 = (Graphics2D)g.create();
    try {
      JTextComponent c = getComponent();
      Container parent = c.getParent();

      if (c.isOpaque() && parent != null) {
        g2.setColor(parent.getBackground());
        g2.fillRect(0, 0, c.getWidth(), c.getHeight());
      }

      if (isSearchField(c)) {
        Rectangle r = getDrawingRect();
        paintSearchField(g2, c, r);
      } else {
        g2.setColor(c.isEnabled() ? c.getBackground() : UIManager.getColor("Button.background"));

        int bw = JBUI.scale(1);
        g2.fillRect(bw, bw, c.getWidth() - bw*2, c.getHeight() - bw*2);
      }
    } finally {
      g2.dispose();
    }
  }

  @Override public Dimension getPreferredSize(JComponent c) {
    Dimension size = super.getPreferredSize(c);
    size.height = isSearchField(c) ? size.height : JBUI.scale(22);
    return size;
  }
}
