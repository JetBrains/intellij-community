/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.ui.ColorPanel;
import com.intellij.ui.Gray;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.MacUIUtil;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;

/**
 * @author Konstantin Bulenkov
 */
public class MacIntelliJTextBorder extends DarculaTextBorder {
  private static final Color OUTLINE_COLOR = Gray.xBC;

  private static float lwImpl(Graphics2D g2) {
    return JBUI.scale(UIUtil.isRetina(g2) ? 0.5f : 1.0f);
  }

  private static float bwImpl() {
    return JBUI.scale(3);
  }

  @Override
  protected float lw(Graphics2D g2) {
    return lwImpl(g2);
  }

  @Override
  protected float bw() {
    return bwImpl();
  }

  protected Color getOutlineColor(boolean enabled) {
    return OUTLINE_COLOR;
  }

  @Override
  protected void paintSearchArea(Graphics2D g, Rectangle r, JTextComponent c, boolean fillBackground) {
    paintMacSearchArea(g, r, c, fillBackground);
  }

  public static void paintMacSearchArea(Graphics2D g, Rectangle r, JTextComponent c, boolean fillBackground) {
    Graphics2D g2 = (Graphics2D)g.create();
    try {
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, MacUIUtil.USE_QUARTZ ? RenderingHints.VALUE_STROKE_PURE : RenderingHints.VALUE_STROKE_NORMALIZE);
      g2.translate(r.x, r.y);

      float arc = JBUI.scale(6);
      float lw = lwImpl(g2);
      float bw = bwImpl();
      Shape outerShape = new RoundRectangle2D.Float(bw, bw, r.width - bw * 2, r.height - bw * 2, arc, arc);
      if (fillBackground) {
        g2.setColor(c.getBackground());
        g2.fill(outerShape);
      }

      Path2D path = new Path2D.Float(Path2D.WIND_EVEN_ODD);
      path.append(outerShape, false);
      path.append(new RoundRectangle2D.Float(bw + lw, bw + lw, r.width - (bw + lw)*2, r.height - (bw + lw)*2, arc-lw, arc-lw), false);

      g2.setColor(OUTLINE_COLOR);
      g2.fill(path);

      if (c.hasFocus()) {
        DarculaUIUtil.paintFocusBorder(g2, r.width, r.height, arc, true);
      }
    }
    finally {
      g2.dispose();
    }
  }

  @Override
  public Insets getBorderInsets(Component c) {
    if (c instanceof JTextField && c.getParent() instanceof ColorPanel) {
      return JBUI.insets(3, 3, 2, 2).asUIResource();
    }
    Insets insets = JBUI.insets(5, 8).asUIResource();
    TextFieldWithPopupHandlerUI.updateBorderInsets(c, insets);
    return insets;
  }
}
