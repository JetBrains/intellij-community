// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui.laf.darcula.ui;

import com.intellij.ide.ui.laf.VisualPaddingsProvider;
import com.intellij.ide.ui.laf.darcula.DarculaUIUtil;
import com.intellij.openapi.ui.ErrorBorderCapable;
import com.intellij.ui.ColorPanel;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.MacUIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.plaf.UIResource;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;

/**
 * @author Konstantin Bulenkov
 */
public class DarculaTextBorder implements Border, UIResource, ErrorBorderCapable, VisualPaddingsProvider {
  @Override
  public Insets getBorderInsets(Component c) {
    if (c instanceof JTextField && c.getParent() instanceof ColorPanel) {
      return JBUI.insets(3, 3, 2, 2).asUIResource();
    }
    Insets insets = JBUI.insets(JBUI.isCompensateVisualPaddingOnComponentLevel(c.getParent()) ? 5 : (int)bw(), 9).asUIResource();
    TextFieldWithPopupHandlerUI.updateBorderInsets(c, insets);
    return insets;
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
    else {
      Graphics2D g2 = (Graphics2D)g.create();
      try {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,
                            MacUIUtil.USE_QUARTZ ? RenderingHints.VALUE_STROKE_PURE : RenderingHints.VALUE_STROKE_NORMALIZE);

        Container parent = c.getParent();
        // if panel layout will compensate visual paddings,  paint as MacComboBoxBorder does - do not translate to avoid complicating code (and logical expectations)
        if (JBUI.isCompensateVisualPaddingOnComponentLevel(parent)) {
          JBInsets.removeFrom(r, JBUI.insets(1));
        }

        g2.translate(r.x, r.y);

        Path2D border = new Path2D.Float(Path2D.WIND_EVEN_ODD);
        float lw = lw(g2);
        float bw = bw();
        border.append(new Rectangle2D.Float(bw, bw, r.width - bw * 2, r.height - bw * 2), false);
        border.append(new Rectangle2D.Float(bw + lw, bw + lw, r.width - (bw + lw) * 2, r.height - (bw + lw) * 2), false);

        boolean editable = !(c instanceof JTextComponent) || ((JTextComponent)c).isEditable();
        g2.setColor(getOutlineColor(c.isEnabled() && editable));
        g2.fill(border);

        if (parent instanceof JComboBox) return;
        paint(c, g2, r.width, r.height, 0);
      }
      finally {
        g2.dispose();
      }
    }
  }

  protected void paint(Component c, Graphics2D g2, int width, int height, float arc) {
    clipForBorder(c, g2, width, height);

    Object op = ((JComponent)c).getClientProperty("JComponent.outline");
    if (op != null) {
      DarculaUIUtil.paintOutlineBorder(g2, width, height, arc, isSymmetric(), isFocused(c),
                                       DarculaUIUtil.Outline.valueOf(op.toString()));
    } else if (isFocused(c)) {
      DarculaUIUtil.paintFocusBorder(g2, width, height, arc, isSymmetric());
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
      float lw = DarculaUIUtil.lw(g);
      float bw = DarculaUIUtil.bw();
      Shape outerShape = new RoundRectangle2D.Float(bw, bw, r.width - bw*2, r.height - bw*2, arc, arc);
      if (fillBackground) {
        g2.setColor(c.getBackground());
        g2.fill(outerShape);
      }

      Path2D path = new Path2D.Float(Path2D.WIND_EVEN_ODD);
      path.append(outerShape, false);
      path.append(new RoundRectangle2D.Float(bw + lw, bw + lw, r.width - (bw + lw)*2, r.height - (bw + lw)*2, arc-lw, arc-lw), false);

      g2.setColor(DarculaUIUtil.getOutlineColor(c.isEnabled() && c.isEditable()));
      g2.fill(path);

      if (c.hasFocus() && c.getClientProperty("JTextField.Search.noBorderRing") != Boolean.TRUE) {
        DarculaUIUtil.paintFocusBorder(g2, r.width, r.height, arc, true);
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
    return DarculaUIUtil.lw(g2);
  }

  protected float bw() {
    return DarculaUIUtil.bw();
  }

  protected Color getOutlineColor(boolean enabled) {
    return DarculaUIUtil.getOutlineColor(enabled);
  }

  @Nullable
  @Override
  public Insets getVisualPaddings(@NotNull Component component) {
    return JBUI.insets((int)bw());
  }
}
