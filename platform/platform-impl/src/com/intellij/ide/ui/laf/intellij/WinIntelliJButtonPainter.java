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
import java.awt.geom.Area;
import java.awt.geom.Rectangle2D;

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
          int d = JBUI.scale(1);
          int dd = JBUI.scale(2);
          final Area s1 = new Area(new Rectangle2D.Float(d, d, width - 2 * d, height - 2 * d));
          final Area s2 = new Area(new Rectangle2D.Float(d + dd, d + dd, width - 2*d - 2*dd, height - 2*d - 2*dd));
          s1.subtract(s2);
          g.fill(s1);
          g.translate(x,y);

          if (hasFocus) {
            //g.setStroke(new BasicStroke(1, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1, new float[]{1}, 1));
            g.setColor(Gray.x0F);
            UIUtil.drawDottedRectangle(g, 2*dd, 2*dd, width - 2*dd - 1, height - 2*dd - 1);
            //g.drawRect(2*dd, 2*dd, width - 4*dd - 1, height - 4*dd - 1);
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
