// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui.laf.darcula.ui;

import com.intellij.ide.ui.laf.VisualPaddingsProvider;
import com.intellij.openapi.ui.ErrorBorderCapable;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.MacUIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.plaf.UIResource;
import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;

import static com.intellij.ide.ui.laf.darcula.DarculaUIUtil.*;

/**
 * @author Konstantin Bulenkov
 */
public class DarculaSpinnerBorder implements Border, UIResource, ErrorBorderCapable, VisualPaddingsProvider {

  @Override
  public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
    Graphics2D g2 = (Graphics2D)g.create();
    Rectangle r = new Rectangle(x, y, width, height);
    JBInsets.removeFrom(r, JBUI.insets(1));

    try {
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,
                          MacUIUtil.USE_QUARTZ ? RenderingHints.VALUE_STROKE_PURE : RenderingHints.VALUE_STROKE_NORMALIZE);

      g2.translate(r.x, r.y);

      float lw = lw(g2);
      float bw = bw();
      float arc = arc();

      Path2D border = new Path2D.Float(Path2D.WIND_EVEN_ODD);
      border.append(new RoundRectangle2D.Float(bw, bw, r.width - bw * 2, r.height - bw * 2, arc, arc), false);
      border.append(new RoundRectangle2D.Float(bw + lw, bw + lw, r.width - (bw + lw) * 2, r.height - (bw + lw) * 2, arc, arc), false);

      g2.setColor(getOutlineColor(c.isEnabled()));
      g2.fill(border);

      Object op = ((JComponent)c).getClientProperty("JComponent.outline");
      if (op != null) {
        paintOutlineBorder(g2, r.width, r.height, arc, true, isFocused(c), Outline.valueOf(op.toString()));
      } else if (isFocused(c)) {
        paintFocusBorder(g2, r.width, r.height, arc, true);
      }
    } finally {
      g2.dispose();
    }
  }

  @Override
  public Insets getBorderInsets(Component c) {
    return JBUI.insets(4, 8, 4, 4).asUIResource();
  }

  @Override
  public boolean isBorderOpaque() {
    return true;
  }

  @Nullable
  @Override
  public Insets getVisualPaddings(@NotNull Component component) {
    return JBUI.insets(3);
  }

  public static boolean isFocused(Component c) {
    if (c.hasFocus()) return true;

    if (c instanceof JSpinner) {
      JSpinner spinner = (JSpinner)c;
      if (spinner.getEditor() != null) {
        synchronized (spinner.getEditor().getTreeLock()) {
          return spinner.getEditor().getComponent(0).hasFocus();
        }
      }
    }
    return false;
  }
}
