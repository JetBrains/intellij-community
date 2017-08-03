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
import com.intellij.ui.ColorPanel;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Path2D;

import static com.intellij.ide.ui.laf.darcula.ui.TextFieldWithPopupHandlerUI.isSearchFieldWithHistoryPopup;
import static com.intellij.ide.ui.laf.intellij.WinIntelliJTextFieldUI.HOVER_PROPERTY;
import static com.intellij.ide.ui.laf.intellij.WinIntelliJTextFieldUI.adjustInWrapperRect;

/**
 * @author Konstantin Bulenkov
 */
public class WinIntelliJTextBorder extends DarculaTextBorder {
  @Override
  public Insets getBorderInsets(Component c) {
    if (c instanceof JTextField && c.getParent() instanceof ColorPanel) {
      return JBUI.insets(3, 3, 2, 2).asUIResource();
    }
    Insets insets = JBUI.insets(4, 5).asUIResource();
    TextFieldWithPopupHandlerUI.updateBorderInsets(c, insets);
    return insets;
  }

  @Override
  public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
    Graphics2D g2 = (Graphics2D)g.create();
    try {
      Rectangle r = new Rectangle(x, y, width, height);

      adjustInWrapperRect(r, c);

      JBInsets.removeFrom(r, JBUI.insets(1));

      Object eop = ((JComponent)c).getClientProperty("JComponent.error.outline");
      if (Registry.is("ide.inplace.errors.outline") && Boolean.parseBoolean(String.valueOf(eop))) {
        DarculaUIUtil.paintErrorBorder(g2, r.width, r.height, 0, true, c.hasFocus());
      } else {
        //boolean editable = !(c instanceof JTextComponent) || ((JTextComponent)c).isEditable();
        JComponent jc = (JComponent)c;
        if (c.hasFocus()) {
          g2.setColor(UIManager.getColor("TextField.focusedBorderColor"));
        } else if (jc.getClientProperty(HOVER_PROPERTY) == Boolean.TRUE) {
          g2.setColor(UIManager.getColor("TextField.hoverBorderColor"));
        } else{
          g2.setColor(UIManager.getColor(jc.isEnabled() ? "TextField.borderColor" : "Button.intellij.native.borderColor"));
        }

        if (!jc.isEnabled()) {
          g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.47f));
        }

        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_NORMALIZE);

        Path2D border = new Path2D.Double(Path2D.WIND_EVEN_ODD);
        border.append(r, false);

        Rectangle innerRect = new Rectangle(r);
        JBInsets.removeFrom(innerRect, JBUI.insets(1));
        border.append(innerRect, false);

        g2.fill(border);
      }
    } finally {
      g2.dispose();
    }
  }
}

