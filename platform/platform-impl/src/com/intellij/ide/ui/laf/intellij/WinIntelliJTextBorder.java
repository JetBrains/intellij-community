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

import com.intellij.ide.ui.laf.darcula.DarculaUIUtil;
import com.intellij.ide.ui.laf.darcula.ui.DarculaTextBorder;
import com.intellij.ide.ui.laf.darcula.ui.TextFieldWithPopupHandlerUI;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.ui.JBUI;

import javax.swing.*;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.geom.Area;
import java.awt.geom.Rectangle2D;

/**
 * @author Konstantin Bulenkov
 */
public class WinIntelliJTextBorder extends DarculaTextBorder {
  @Override
  public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
    if (TextFieldWithPopupHandlerUI.isSearchField(c)) return;

    Graphics2D g2 = (Graphics2D)g.create();
    try {
      g2.translate(x, y);
      boolean editable = !(c instanceof JTextComponent) || ((JTextComponent)c).isEditable();

      Object eop = ((JComponent)c).getClientProperty("JComponent.error.outline");
      if (Registry.is("ide.inplace.errors.outline") && Boolean.parseBoolean(String.valueOf(eop))) {
        DarculaUIUtil.paintErrorBorder(g2, width, height, 0, c.hasFocus(), true);
      } else {
        int d = JBUI.scale(1);
        int dd = JBUI.scale(2);
        if (c.hasFocus()) {
          g2.setColor(getBorderColor(c.isEnabled() && editable, true));

          Area s1 = new Area(new Rectangle2D.Float(d, d, width - 2 * d, height - 2 * d));
          Area s2 = new Area(new Rectangle2D.Float(d + dd, d + dd, width - 2*d - 2*dd, height - 2*d - 2*dd));
          s1.subtract(s2);
          g2.fill(s1);
        }
        else {
          g2.setColor(getBorderColor(c.isEnabled() && editable, false));
          g2.drawRect(d, d, width - 2 * d, height - 2 * d);
        }
      }
    } finally {
      g2.dispose();
    }
  }

  private static Color getBorderColor(boolean enabled, boolean focus) {
    if (focus) {
      return UIManager.getColor("TextField.activeBorderColor");
    }
    return UIManager.getColor(enabled ? "TextField.borderColor" : "disabledBorderColor");
  }
}

