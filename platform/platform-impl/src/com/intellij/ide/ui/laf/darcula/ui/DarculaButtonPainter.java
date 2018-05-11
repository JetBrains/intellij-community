// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui.laf.darcula.ui;

import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.MacUIUtil;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.plaf.UIResource;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;

import static com.intellij.ide.ui.laf.darcula.DarculaUIUtil.*;
import static com.intellij.ide.ui.laf.darcula.ui.DarculaButtonUI.*;

/**
 * @author Konstantin Bulenkov
 */
public class DarculaButtonPainter implements Border, UIResource {
  private static final int myOffset = 4;

  @Override
  public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
    Graphics2D g2 = (Graphics2D)g.create();

    try {
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,
                          MacUIUtil.USE_QUARTZ ? RenderingHints.VALUE_STROKE_PURE : RenderingHints.VALUE_STROKE_NORMALIZE);

      boolean isSmallComboButton = isSmallComboButton(c);
      int diam = HELP_BUTTON_DIAMETER.get();
      float arc = BUTTON_ARC.getFloat();
      float lw = LW.getFloat();
      float bw = isSmallComboButton ? 0 : BW.getFloat();

      Rectangle r = new Rectangle(x, y, width, height);
      boolean paintComboFocus = isSmallComboButton && c.isFocusable() && c.hasFocus();
      if (paintComboFocus) { // a11y support
        g2.setColor(JBUI.CurrentTheme.Focus.focusColor());

        Path2D border = new Path2D.Float(Path2D.WIND_EVEN_ODD);
        border.append(new RoundRectangle2D.Float(r.x, r.y, r.width, r.height, arc + lw, arc + lw), false);
        border.append(new RoundRectangle2D.Float(r.x + lw*2, r.y + lw*2, r.width - lw * 4, r.height - lw * 4, arc, arc), false);
        g2.fill(border);
      }

      JBInsets.removeFrom(r, JBUI.insets(1));
      g2.translate(r.x, r.y);

      if (!isSmallComboButton) {
        if (c.hasFocus()) {
          if (UIUtil.isHelpButton(c)) {
            paintFocusOval(g2, (r.width - diam) / 2.0f, (r.height - diam) / 2.0f, diam, diam);
          } else {
            Outline type = isDefaultButton((JComponent)c) ? Outline.defaultButton : Outline.focus;
            paintOutlineBorder(g2, r.width, r.height, arc, true, true, type);
          }
        }
      }

      g2.setPaint(getBorderPaint(c));

      if (UIUtil.isHelpButton(c)) {
        g2.draw(new Ellipse2D.Float((r.width - diam) / 2.0f, (r.height - diam) / 2.0f, diam, diam));
      } else if (!paintComboFocus) {
        Path2D border = new Path2D.Float(Path2D.WIND_EVEN_ODD);
        border.append(new RoundRectangle2D.Float(bw, bw, r.width - bw * 2, r.height - bw * 2, arc, arc), false);
        border.append(new RoundRectangle2D.Float(bw + lw, bw + lw, r.width - (bw + lw) * 2, r.height - (bw + lw) * 2, arc - lw, arc - lw),
                      false);

        g2.fill(border);
      }
    } finally {
      g2.dispose();
    }
  }

  public Paint getBorderPaint(Component button) {
    AbstractButton b = (AbstractButton)button;
    Color borderColor = (Color)b.getClientProperty("JButton.borderColor");
    return button.isEnabled() ? borderColor != null ? borderColor :
     button.hasFocus() ?
        UIManager.getColor(isDefaultButton(b) ? "Button.darcula.defaultFocusedOutlineColor" : "Button.darcula.focusedOutlineColor") :
        UIManager.getColor(button.isEnabled() && isDefaultButton(b) ? "Button.darcula.defaultOutlineColor" : "Button.darcula.outlineColor")

     : UIManager.getColor("Button.darcula.disabledOutlineColor");
  }

  @Override
  public Insets getBorderInsets(Component c) {
    return JBUI.insets(3).asUIResource();
  }

  protected int getOffset() {
    return myOffset;
  }

  @Override
  public boolean isBorderOpaque() {
    return false;
  }
}
