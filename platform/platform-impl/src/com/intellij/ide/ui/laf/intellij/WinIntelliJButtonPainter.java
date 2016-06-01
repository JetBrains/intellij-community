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

import com.intellij.ide.ui.laf.darcula.ui.DarculaButtonUI;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.ui.Gray;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.plaf.UIResource;
import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
public class WinIntelliJButtonPainter implements Border, UIResource {
  private static final int myOffset = 4;

    @Override
    public void paintBorder(Component c, Graphics graphics, int x, int y, int width, int height) {
      Graphics2D g = (Graphics2D)graphics;
      final boolean hasFocus = c.hasFocus();
      final boolean isDefault = DarculaButtonUI.isDefaultButton((JComponent)c);
      if (hasFocus || isDefault) {
        if (DarculaButtonUI.isHelpButton((JComponent)c)) {
            //todo
        } else {
          g.setColor(UIManager.getColor("Button.intellij.native.activeBorderColor"));
          g.setStroke(new BasicStroke(JBUI.scale(2f)));
          g.translate(x,y);
          g.drawRect(JBUI.scale(1), JBUI.scale(1), width-2*JBUI.scale(1), height-2*JBUI.scale(1));

          if (hasFocus) {
            g.setStroke(new BasicStroke(JBUI.scale(1f)));
            g.setColor(Gray.x00);
            UIUtil.drawDottedRectangle(g, JBUI.scale(1) + 1, JBUI.scale(1) + 1, width-2*JBUI.scale(1) - 1, height-2*JBUI.scale(1) - 1);
          }
          g.translate(-x,-y);
        }
      } else {
        g.setColor(UIManager.getColor("Button.intellij.native.borderColor"));
        if (!DarculaButtonUI.isHelpButton((JComponent)c)) {
          g.translate(x, y);
          g.drawRect(0, 0, width - 1, height - 1);
          g.translate(-x, -y);
        }
      }
    }

    @Override
    public Insets getBorderInsets(Component c) {
      if (c.getParent() instanceof ActionToolbar) {
        return JBUI.insets(4, 16).asUIResource();
      }
      if (DarculaButtonUI.isSquare(c)) {
        return JBUI.insets(2, 0).asUIResource();
      }
      return JBUI.insets(3, 17).asUIResource();
    }

    protected int getOffset() {
      return myOffset;
    }

    @Override
    public boolean isBorderOpaque() {
      return false;
    }
}
