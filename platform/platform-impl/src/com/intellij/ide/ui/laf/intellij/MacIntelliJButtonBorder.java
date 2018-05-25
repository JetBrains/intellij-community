// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui.laf.intellij;

import com.intellij.ide.ui.laf.darcula.DarculaUIUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.MacUIUtil;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.plaf.UIResource;
import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;

import static com.intellij.ide.ui.laf.darcula.ui.DarculaButtonUI.isSmallComboButton;
import static com.intellij.ide.ui.laf.intellij.MacIntelliJTextBorder.ARC;

/**
 * @author Konstantin Bulenkov
 */
public class MacIntelliJButtonBorder implements Border, UIResource {
  @Override
  public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
    if (!c.hasFocus() || c instanceof JComponent && UIUtil.isHelpButton(c)) return;

    Graphics2D g2 = (Graphics2D)g.create();
    try {
      g2.translate(x, y);

      float arc = ARC.getFloat();

      if (isSmallComboButton(c) && c.isFocusable() && c.hasFocus()) {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, MacUIUtil.USE_QUARTZ ? RenderingHints.VALUE_STROKE_PURE : RenderingHints.VALUE_STROKE_NORMALIZE);

        float lw = JBUI.scale(UIUtil.isRetina(g2) ? 0.5f : 1.0f);

        Path2D border = new Path2D.Float(Path2D.WIND_EVEN_ODD);
        border.append(new RoundRectangle2D.Float(0, 0, width, height, arc + lw, arc + lw), false);
        border.append(new RoundRectangle2D.Float(lw*2, lw*2, width - lw*4, height - lw*4, arc, arc), false);

        g2.setColor(JBUI.CurrentTheme.Focus.focusColor());
        g2.fill(border);
      } else {
        DarculaUIUtil.paintFocusBorder(g2, width, height, arc, true);
      }
    } finally {
      g2.dispose();
    }
  }

  @Override
  public Insets getBorderInsets(Component c) {
    return JBUI.insets(3).asUIResource();
  }

  @Override
  public boolean isBorderOpaque() {
    return false;
  }
}
