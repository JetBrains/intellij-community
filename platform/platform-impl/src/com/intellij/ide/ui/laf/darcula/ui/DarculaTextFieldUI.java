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

import com.intellij.openapi.ui.GraphicsConfig;
import com.intellij.util.ui.JBInsets;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicTextFieldUI;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;

/**
 * @author Konstantin Bulenkov
 */
public class DarculaTextFieldUI extends BasicTextFieldUI {

  @SuppressWarnings("MethodOverridesStaticMethodOfSuperclass")
  public static ComponentUI createUI(final JComponent c) {
    c.addFocusListener(new FocusAdapter() {
      @Override
      public void focusGained(FocusEvent e) {
        c.repaint();
      }

      @Override
      public void focusLost(FocusEvent e) {
        c.repaint();
      }
    });
    return new DarculaTextFieldUI();
  }

  @Override
  protected void installDefaults() {
    super.installDefaults();
  }

  @Override
  protected void paintSafely(Graphics g) {
    super.paintSafely(g);
  }

  @Override
  protected void paintBackground(Graphics g) {
    final JTextComponent c = getComponent();
    final Container parent = c.getParent();
    if (parent != null) {
      g.setColor(parent.getBackground());
      g.fillRect(0,0,c.getWidth(), c.getHeight());
    }
    final Border border = c.getBorder();
    if (border instanceof DarculaTextBorder) {
      g.setColor(c.getBackground());
      final int width = c.getWidth();
      final int height = c.getHeight();
      final JBInsets insets = ((DarculaTextBorder)border).getBorderInsets(c);
      if (c.hasFocus()) {
        final GraphicsConfig config = new GraphicsConfig(g);
        ((Graphics2D)g).setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        ((Graphics2D)g).setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_NORMALIZE);

        g.fillRoundRect(insets.left-5, insets.top-2, width - insets.width() + 10, height - insets.height() + 6, 5, 5);
        config.restore();
      } else {
        g.fillRect(insets.left-5, insets.top-2, width - insets.width() + 12, height - insets.height() + 6);
      }
    } else {
      super.paintBackground(g);
    }
  }
}
