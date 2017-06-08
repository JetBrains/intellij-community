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
import com.intellij.ui.components.panels.Wrapper;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.plaf.UIResource;
import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;

import static com.intellij.ide.ui.laf.darcula.ui.DarculaButtonUI.isSquare;
import static com.intellij.ide.ui.laf.intellij.WinIntelliJButtonUI.DISABLED_ALPHA_LEVEL;

/**
 * @author Konstantin Bulenkov
 */
public class WinIntelliJButtonBorder implements Border, UIResource {
  @Override
  public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
    paint(c, g, x, y, width, height);
  }

  static void paint(Component c, Graphics g, int x, int y, int width, int height) {
    if (!(c instanceof AbstractButton) || DarculaButtonUI.isHelpButton((JComponent)c)) return;

    Graphics2D g2 = (Graphics2D)g.create();
    AbstractButton b = (AbstractButton)c;
    ButtonModel bm = b.getModel();
    Rectangle r = new Rectangle(x, y, width, height);
    try {
      if (isSquare(c)) {
        JBInsets.removeFrom(r, b.getInsets());
      }

      g2.translate(r.x, r.y);
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_NORMALIZE);

      Color color = UIManager.getColor("Button.intellij.native.borderColor");
      int bw = JBUI.scale(1);

      if (!c.isEnabled()) {
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, DISABLED_ALPHA_LEVEL));
      } else if (bm.isPressed()) {
        color = UIManager.getColor("Button.intellij.native.pressedBorderColor");
      } else if (b.hasFocus() || bm.isRollover()) {
        color = UIManager.getColor("Button.intellij.native.focusedBorderColor");
      }  else {
        if (DarculaButtonUI.isDefaultButton(b)) {
          bw = JBUI.scale(2);
          color = UIManager.getColor("Button.intellij.native.focusedBorderColor");
        }
      }

      Path2D border = new Path2D.Double(Path2D.WIND_EVEN_ODD);
      border.append(new Rectangle2D.Double(0, 0, r.width, r.height), false);
      border.append(new Rectangle2D.Double(bw, bw, r.width - 2*bw, r.height - 2*bw), false);

      g2.setColor(color);
      g2.fill(border);
    } finally {
      g2.dispose();
    }
  }

  @Override
  public Insets getBorderInsets(Component c) {
    if (c.getParent() instanceof ActionToolbar) {
      return JBUI.insets(4, 16).asUIResource();
    } else if (DarculaButtonUI.isSquare(c)) {
      int i = (UIUtil.getParentOfType(Wrapper.class, c) != null) ? 1 : 0;
      return JBUI.insets(i).asUIResource();
    } else if (DarculaButtonUI.isHelpButton((JComponent)c)) {
      return JBUI.insets(0, 0, 0, 10).asUIResource();
    } else {
      return JBUI.insets(3, 17).asUIResource();
    }
  }

  @Override
  public boolean isBorderOpaque() {
    return false;
  }
}
