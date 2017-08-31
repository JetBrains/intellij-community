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

import com.intellij.ide.ui.laf.darcula.ui.DarculaSpinnerBorder;
import com.intellij.openapi.ui.ErrorBorderCapable;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Path2D;

import static com.intellij.ide.ui.laf.darcula.DarculaUIUtil.ACTIVE_ERROR_COLOR;
import static com.intellij.ide.ui.laf.darcula.DarculaUIUtil.INACTIVE_ERROR_COLOR;

public class WinIntelliJSpinnerBorder extends DarculaSpinnerBorder implements ErrorBorderCapable {
  @Override
  public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
    if (!(c instanceof JSpinner)) return;

    JSpinner spinner = (JSpinner)c;
    Graphics2D g2 = (Graphics2D)g.create();
    try {
      Rectangle r = new Rectangle(x, y, width, height);

      int bw = 1;
      if (spinner.getClientProperty("JComponent.error.outline") == Boolean.TRUE) {
        g2.setColor(c.hasFocus() ? ACTIVE_ERROR_COLOR : INACTIVE_ERROR_COLOR);
        bw = 2;
      } else if (c.isEnabled()) {
        boolean hover = spinner.getClientProperty(WinIntelliJSpinnerUI.HOVER_PROPERTY) == Boolean.TRUE;
        if (DarculaSpinnerBorder.isFocused(c)) {
          g2.setColor(UIManager.getColor("TextField.focusedBorderColor"));
        } else {
          g2.setColor(UIManager.getColor(hover ? "TextField.hoverBorderColor" : "TextField.borderColor"));
        }
        JBInsets.removeFrom(r, JBUI.insets(1, 1, 1, WinIntelliJSpinnerUI.BUTTON_WIDTH - 1));
      } else {
        g2.setColor(UIManager.getColor("Button.intellij.native.borderColor"));
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.35f));
        JBInsets.removeFrom(r, JBUI.insets(1, 1, 1, WinIntelliJSpinnerUI.BUTTON_WIDTH - 1));
      }

      Path2D border = new Path2D.Double(Path2D.WIND_EVEN_ODD);
      border.append(r, false);

      Rectangle innerRect = new Rectangle(r);
      JBInsets.removeFrom(innerRect, JBUI.insets(bw));
      border.append(innerRect, false);

      g2.fill(border);

    } finally {
      g2.dispose();
    }
  }

  @Override
  public Insets getBorderInsets(Component c) {
    return new JBInsets(2, 2, 2, 2).asUIResource();
  }
}
