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
import com.intellij.ide.ui.laf.darcula.ui.TextFieldWithPopupHandlerUI;
import com.intellij.openapi.ui.GraphicsConfig;
import com.intellij.ui.ColorPanel;
import com.intellij.util.ui.JBUI;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.plaf.UIResource;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.geom.Area;
import java.awt.geom.Rectangle2D;

/**
 * @author Konstantin Bulenkov
 */
public class WinIntelliJTextBorder implements Border, UIResource {
  @Override
  public Insets getBorderInsets(Component c) {
    int vOffset = TextFieldWithPopupHandlerUI.isSearchField(c) ? 6 : 4;
    if (TextFieldWithPopupHandlerUI.isSearchFieldWithHistoryPopup(c)) {
      return JBUI.insets(vOffset, 7 + 16 + 3, vOffset, 7 + 16).asUIResource();
    }
    else if (TextFieldWithPopupHandlerUI.isSearchField(c)) {
      return JBUI.insets(vOffset, 4 + 16 + 3, vOffset, 7 + 16).asUIResource();
    }
    else if (c instanceof JTextField && c.getParent() instanceof ColorPanel) {
      return JBUI.insets(3, 3, 2, 2).asUIResource();
    }
    else {
      return JBUI.insets(vOffset, 7, vOffset, 7).asUIResource();
    }
  }

  @Override
  public boolean isBorderOpaque() {
    return false;
  }

  @Override
  public void paintBorder(Component c, Graphics g2, int x, int y, int width, int height) {
    if (DarculaTextFieldUI.isSearchField(c)) return;
    Graphics2D g = (Graphics2D)g2;
    final GraphicsConfig config = new GraphicsConfig(g);
    g.translate(x, y);
    boolean editable = !(c instanceof JTextComponent) || ((JTextComponent)c).isEditable();

    final int d = JBUI.scale(1);
    final int dd = JBUI.scale(2);
    if (c.hasFocus()) {
      g.setColor(getBorderColor(c.isEnabled() && editable, true));
      final Area s1 = new Area(new Rectangle2D.Float(d, d, width - 2 * d, height - 2 * d));
      final Area s2 = new Area(new Rectangle2D.Float(d + dd, d + dd, width - 2*d - 2*dd, height - 2*d - 2*dd));
      s1.subtract(s2);
      g.fill(s1);
    }
    else {
      g.setColor(getBorderColor(c.isEnabled() && editable, false));
      g.drawRect(d, d, width - 2 * d, height - 2 * d);
    }
    g.translate(-x, -y);
    config.restore();
  }

  private static Color getBorderColor(boolean enabled, boolean focus) {
    if (focus) {
      return UIManager.getColor("TextField.activeBorderColor");
    }
    return UIManager.getColor(enabled ? "TextField.borderColor" : "disabledBorderColor");
  }
}

