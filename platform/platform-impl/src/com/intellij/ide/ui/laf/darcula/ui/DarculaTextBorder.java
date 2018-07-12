// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui.laf.darcula.ui;

import com.intellij.ide.ui.laf.darcula.DarculaUIUtil;
import com.intellij.openapi.ui.ErrorBorderCapable;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.MacUIUtil;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.plaf.UIResource;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;

import static com.intellij.ide.ui.laf.darcula.DarculaUIUtil.*;

/**
 * @author Konstantin Bulenkov
 */
public class DarculaTextBorder implements Border, UIResource, ErrorBorderCapable {
  @Override
  public Insets getBorderInsets(Component c) {
    return JBUI.insets(3).asUIResource();
  }

  @Override
  public boolean isBorderOpaque() {
    return false;
  }

  @Override
  public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
    if (((JComponent)c).getClientProperty("JTextField.Search.noBorderRing") == Boolean.TRUE) return;

    Rectangle r = new Rectangle(x, y, width, height);

    if (TextFieldWithPopupHandlerUI.isSearchField(c)) {
      paintSearchArea((Graphics2D)g, r, (JTextComponent)c, false);
    }
    else if (!(c.getParent() instanceof JComboBox)){
      Graphics2D g2 = (Graphics2D)g.create();
      try {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,
                            MacUIUtil.USE_QUARTZ ? RenderingHints.VALUE_STROKE_PURE : RenderingHints.VALUE_STROKE_NORMALIZE);

        JBInsets.removeFrom(r, paddings());
        g2.translate(r.x, r.y);

        float lw = lw(g2);
        float bw = bw();

        clipForBorder(c, g2, r.width, r.height);

        Object op = ((JComponent)c).getClientProperty("JComponent.outline");
        boolean focused = isFocused(c);
        if (op != null) {
          paintOutlineBorder(g2, r.width, r.height, 0, isSymmetric(), focused, Outline.valueOf(op.toString()));
        } else {
          if (focused) {
            paintOutlineBorder(g2, r.width, r.height, 0, isSymmetric(), true, Outline.focus);
          }
          Path2D border = new Path2D.Float(Path2D.WIND_EVEN_ODD);
          border.append(new Rectangle2D.Float(bw, bw, r.width - bw * 2, r.height - bw * 2), false);
          border.append(new Rectangle2D.Float(bw + lw, bw + lw, r.width - (bw + lw) * 2, r.height - (bw + lw) * 2), false);

          boolean editable = !(c instanceof JTextComponent) || ((JTextComponent)c).isEditable();
          g2.setColor(getOutlineColor(c.isEnabled() && editable, c.hasFocus()));
          g2.fill(border);
        }
      }
      finally {
        g2.dispose();
      }
    }
  }

  protected void paintSearchArea(Graphics2D g, Rectangle r, JTextComponent c, boolean fillBackground) {
    paintDarculaSearchArea(g, r, c, fillBackground);
  }

  public static void paintDarculaSearchArea(Graphics2D g, Rectangle r, JTextComponent c, boolean fillBackground) {
    Graphics2D g2 = (Graphics2D)g.create();
    try {
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,
                          MacUIUtil.USE_QUARTZ ? RenderingHints.VALUE_STROKE_PURE : RenderingHints.VALUE_STROKE_NORMALIZE);

      JBInsets.removeFrom(r, JBUI.insets(1));
      g2.translate(r.x, r.y);

      float arc = JBUI.scale(6f);
      float lw = LW.getFloat();
      float bw = BW.getFloat();
      Shape outerShape = new RoundRectangle2D.Float(bw, bw, r.width - bw*2, r.height - bw*2, arc, arc);
      if (fillBackground) {
        g2.setColor(c.getBackground());
        g2.fill(outerShape);
      }

      if (c.getClientProperty("JTextField.Search.noBorderRing") != Boolean.TRUE) {
        if (c.hasFocus()) {
          paintFocusBorder(g2, r.width, r.height, arc, true);
        }
        Path2D path = new Path2D.Float(Path2D.WIND_EVEN_ODD);
        path.append(outerShape, false);
        path.append(new RoundRectangle2D.Float(bw + lw, bw + lw, r.width - (bw + lw)*2, r.height - (bw + lw)*2, arc-lw, arc-lw), false);

        g2.setColor(DarculaUIUtil.getOutlineColor(c.isEnabled() && c.isEditable(), c.hasFocus()));
        g2.fill(path);
      }
    } finally {
      g2.dispose();
    }
  }

  protected boolean isFocused(Component c) {
    return c instanceof JScrollPane ? ((JScrollPane)c).getViewport().getView().hasFocus() :c.hasFocus();
  }

  protected void clipForBorder(Component c, Graphics2D g2, int width, int height) {
    Area area = new Area(new Rectangle2D.Float(0, 0, width, height));
    float lw = lw(g2);
    float bw = bw();
    area.subtract(new Area(new Rectangle2D.Float(bw + lw, bw + lw, width - (bw + lw) * 2, height - (bw + lw) * 2)));
    area.intersect(new Area(g2.getClip()));
    g2.setClip(area);
  }

  protected boolean isSymmetric() {
    return true;
  }

  protected float lw(Graphics2D g2) {
    return LW.getFloat();
  }

  protected float bw() {
    return BW.getFloat();
  }

  protected Color getOutlineColor(boolean enabled, boolean focused) {
    return DarculaUIUtil.getOutlineColor(enabled, focused);
  }

  protected Insets paddings() {
    return DarculaUIUtil.paddings();
  }
}
