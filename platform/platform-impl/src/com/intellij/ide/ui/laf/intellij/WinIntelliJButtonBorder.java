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
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.plaf.UIResource;
import java.awt.*;
import java.awt.geom.Path2D;

import static com.intellij.ide.ui.laf.darcula.ui.DarculaButtonUI.isSquare;
import static com.intellij.ide.ui.laf.intellij.WinIntelliJButtonUI.DISABLED_ALPHA_LEVEL;

/**
 * @author Konstantin Bulenkov
 */
public class WinIntelliJButtonBorder implements Border, UIResource {
  @Override
  public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
    if (!(c instanceof AbstractButton) || UIUtil.isHelpButton(c)) return;

    Graphics2D g2 = (Graphics2D)g.create();
    AbstractButton b = (AbstractButton)c;
    ButtonModel bm = b.getModel();
    Rectangle outerRect = new Rectangle(x, y, width, height);
    try {
      JBInsets.removeFrom(outerRect, getOuterInsets());
      if (UIUtil.getParentOfType(ActionToolbar.class, c) != null) {
        JBInsets.removeFrom(outerRect, JBUI.insetsRight(3));
      }

      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_NORMALIZE);

      Color color = UIManager.getColor("Button.intellij.native.borderColor");
      if (!c.isEnabled()) {
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, DISABLED_ALPHA_LEVEL));
      } else if (bm.isPressed()) {
        color = UIManager.getColor("Button.intellij.native.pressedBorderColor");
      } else if (b.hasFocus() || bm.isRollover()) {
        color = UIManager.getColor("Button.intellij.native.focusedBorderColor");
      }  else {
        if (DarculaButtonUI.isDefaultButton(b)) {
          color = UIManager.getColor("Button.intellij.native.focusedBorderColor");
        }
      }
      int bw = getBorderWidth(b);

      Path2D border = new Path2D.Float(Path2D.WIND_EVEN_ODD);
      border.append(outerRect, false);

      Rectangle innerRect = new Rectangle(outerRect);
      JBInsets.removeFrom(innerRect, JBUI.insets(bw));
      border.append(innerRect, false);

      g2.setColor(color);
      g2.fill(border);
    } finally {
      g2.dispose();
    }
  }

  @NotNull
  public JBInsets getOuterInsets() {
    return JBUI.insets(1);
  }

  public static boolean isWideBorder(@NotNull AbstractButton b) {
    ButtonModel bm = b.getModel();
    return b.isEnabled() && !bm.isPressed() && !b.hasFocus() && !bm.isRollover() && DarculaButtonUI.isDefaultButton(b);
  }

  public static int getBorderWidth(@NotNull AbstractButton b) {
    return isWideBorder(b) ? 2 : 1;
  }

  @Override
  public Insets getBorderInsets(Component c) {
    if (isSquare(c)) {
      return JBUI.insets(2).asUIResource();
    } else if (UIUtil.isHelpButton(c)) {
      return JBUI.insets(0, 0, 0, 10).asUIResource();
    } else if (DarculaButtonUI.isComboButton((JComponent)c)) {
      return JBUI.insets(4, 10).asUIResource();
    } else {
      return JBUI.insets(4, 18).asUIResource();
    }
  }

  @Override
  public boolean isBorderOpaque() {
    return false;
  }
}
