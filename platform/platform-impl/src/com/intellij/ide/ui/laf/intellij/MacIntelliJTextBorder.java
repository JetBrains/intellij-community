// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui.laf.intellij;

import com.intellij.ide.ui.laf.darcula.DarculaUIUtil;
import com.intellij.ide.ui.laf.darcula.ui.DarculaTextBorder;
import com.intellij.ui.Gray;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.JBValue;
import com.intellij.util.ui.MacUIUtil;
import com.intellij.util.ui.UIUtil;

import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;

/**
 * @author Konstantin Bulenkov
 */
public class MacIntelliJTextBorder extends DarculaTextBorder {
  private static final Color OUTLINE_COLOR = Gray.xBC;
  private static final Insets PADDINGS = JBUI.emptyInsets();

  static final JBValue MINIMUM_HEIGHT = new JBValue.Float(21);
  static final JBValue BW = new JBValue.Float(3);
  static final JBValue ARC = new JBValue.Float(6);
  static float LW(Graphics2D g2) {
    return JBUI.scale(UIUtil.isRetina(g2) ? 0.5f : 1.0f);
  }

  @Override
  protected float lw(Graphics2D g2) {
    return LW(g2);
  }

  @Override
  protected float bw() {
    return BW.getFloat();
  }

  @Override
  protected Color getOutlineColor(boolean enabled, boolean focused) {
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

      float arc = ARC.getFloat();
      float lw = LW(g2);
      float bw = BW.getFloat();
      Shape outerShape = new RoundRectangle2D.Float(bw, bw, r.width - bw * 2, r.height - bw * 2, arc, arc);
      if (fillBackground) {
        g2.setColor(c.getBackground());
        g2.fill(outerShape);
      }

      Path2D path = new Path2D.Float(Path2D.WIND_EVEN_ODD);
      path.append(outerShape, false);
      path.append(new RoundRectangle2D.Float(bw + lw, bw + lw, r.width - (bw + lw)*2, r.height - (bw + lw)*2, arc - lw, arc - lw), false);

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
  protected Insets paddings() {
    return PADDINGS;
  }
}
