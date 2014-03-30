/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.ide.ui.laf.darcula.DarculaUIUtil;
import com.intellij.openapi.ui.GraphicsConfig;
import com.intellij.ui.Gray;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.plaf.InsetsUIResource;
import javax.swing.plaf.UIResource;
import javax.swing.text.JTextComponent;
import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
public class DarculaTextBorder implements Border, UIResource {
  @Override
  public Insets getBorderInsets(Component c) {
    int vOffset = c instanceof JPasswordField ? 3 : 4;
    if (DarculaTextFieldUI.isSearchField(c)) {
      vOffset += 2;
    }
    if (DarculaTextFieldUI.isSearchFieldWithHistoryPopup(c)) {
      return new InsetsUIResource(vOffset, 7 + 16 + 3, vOffset, 7 + 16);
    } else if (DarculaTextFieldUI.isSearchField(c)) {
      return new InsetsUIResource(vOffset, 4 + 16 + 3, vOffset, 7 + 16);
    } else {
      return new InsetsUIResource(vOffset, 7, vOffset, 7);
    }
  }

  @Override
  public boolean isBorderOpaque() {
    return false;
  }

  @Override
  public void paintBorder(Component c, Graphics g2, int x, int y, int width, int height) {
    if (DarculaTextFieldUI.isSearchField(c)) return;
    Graphics2D g = ((Graphics2D)g2);
    final GraphicsConfig config = new GraphicsConfig(g);
    g.translate(x, y);

    if (c.hasFocus()) {
      DarculaUIUtil.paintFocusRing(g, 2, 2, width-4, height-4);
    } else {
      boolean editable = !(c instanceof JTextComponent) || (((JTextComponent)c).isEditable());
      g.setColor(c.isEnabled() && editable ? Gray._100 : new Color(0x535353));
      g.drawRect(1, 1, width - 2, height - 2);
    }
    g.translate(-x, -y);
    config.restore();
  }
}
