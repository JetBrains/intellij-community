// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.laf.darcula.ui;

import com.intellij.ui.JBColor;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.MacUIUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

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

      boolean isSmallComboButton = isSmallVariant(c);
      boolean isDefaultButton = isDefaultButton((JComponent)c);
      boolean isGotItButton = isGotItButton(c);
      int diam = HELP_BUTTON_DIAMETER.get();
      float lw = LW.getFloat();
      float bw = isSmallComboButton || isGotItButton ? 0 : BW.getFloat();
      float arc = isTag(c) ? height - bw * 2 - lw * 2: BUTTON_ARC.getFloat();

      Rectangle r = new Rectangle(x, y, width, height);
      boolean paintComboFocus = isSmallComboButton && c.isFocusable() && c.hasFocus();
      if (paintComboFocus) { // a11y support
        g2.setColor(JBUI.CurrentTheme.Focus.focusColor());

        Path2D border = new Path2D.Float(Path2D.WIND_EVEN_ODD);
        border.append(new RoundRectangle2D.Float(r.x, r.y, r.width, r.height, arc + lw, arc + lw), false);
        border.append(new RoundRectangle2D.Float(r.x + lw * 2, r.y + lw * 2, r.width - lw * 4, r.height - lw * 4, arc, arc), false);
        g2.fill(border);
      }

      if (!isGotItButton) JBInsets.removeFrom(r, JBUI.insets(1));

      g2.translate(r.x, r.y);

      boolean newUiBorderPainted = false;
      if (!isSmallComboButton && !isGotItButton) {
        if (c.hasFocus()) {
          if (UIUtil.isHelpButton(c)) {
            paintFocusOval(g2, (r.width - diam) / 2.0f, (r.height - diam) / 2.0f, diam, diam);
          }
          else if (isTag(c)) {
            paintTag(g2, r.width, r.height, c.hasFocus(), computeOutlineFor(c));
          }
          else {
            newUiBorderPainted = paintNormalFocusBorder((Graphics2D) g, (JComponent)c, new Rectangle(x, y, width, height));
            if (!newUiBorderPainted) {
              Outline type = isDefaultButton ? Outline.defaultButton : Outline.focus;
              paintOutlineBorder(g2, r.width, r.height, arc, true, true, type);
            }
          }
        }
        else if (isTag(c)) {
          paintTag(g2, r.width, r.height, c.hasFocus(), computeOutlineFor(c));
        }
      }

      g2.setPaint(getBorderPaint(c));

      if (UIUtil.isHelpButton(c)) {
        g2.draw(new Ellipse2D.Float((r.width - diam) / 2.0f, (r.height - diam) / 2.0f, diam, diam));
      }
      else if (!paintComboFocus && !newUiBorderPainted) {
        Path2D border = new Path2D.Float(Path2D.WIND_EVEN_ODD);
        border.append(new RoundRectangle2D.Float(bw, bw, r.width - bw * 2, r.height - bw * 2, arc, arc), false);

        if (!isGotItButton) {
          arc = arc > lw ? arc - lw : 0.0f;
        }
        border.append(new RoundRectangle2D.Float(bw + lw, bw + lw, r.width - (bw + lw) * 2, r.height - (bw + lw) * 2, arc, arc), false);

        g2.fill(border);
      }
    }
    finally {
      g2.dispose();
    }
  }

  public Paint getBorderPaint(Component button) {
    return getBorderPaint(button, button.hasFocus());
  }

  @ApiStatus.Internal
  protected boolean paintNormalFocusBorder(@NotNull Graphics2D g, @NotNull JComponent c, @NotNull Rectangle r) {
    return false;
  }

  Paint getBorderPaint(Component button, boolean hasFocus) {
    AbstractButton b = (AbstractButton)button;
    Color borderColor = (Color)b.getClientProperty("JButton.borderColor");
    Rectangle r = new Rectangle(b.getSize());
    JBInsets.removeFrom(r, b.getInsets());
    boolean defButton = isDefaultButton(b);

    if (button.isEnabled()) {
      if (borderColor != null) {
        return borderColor;
      }
      else if (isGotItButton(button)) {
        return new GradientPaint(0, 0, getGotItBorderColorStart(b), 0, r.height, getGotItBorderColorEnd(b));
      }
      else if (hasFocus) {
        return JBUI.CurrentTheme.Button.focusBorderColor(defButton);
      }
      else {
        return new GradientPaint(0, 0, JBUI.CurrentTheme.Button.buttonOutlineColorStart(defButton),
                                 0, r.height, JBUI.CurrentTheme.Button.buttonOutlineColorEnd(defButton));
      }
    }
    else {
      return JBUI.CurrentTheme.Button.disabledOutlineColor();
    }
  }

  @Override
  public Insets getBorderInsets(Component c) {
    if (isGotItButton(c)) {
      return JBInsets.emptyInsets().asUIResource();
    }
    Insets customInsets = getCustomButtonInsets(c);
    if (customInsets != null) {
      return customInsets;
    }

    return isSmallVariant(c) ? JBInsets.create(1, 2).asUIResource() : new JBInsets(3).asUIResource();
  }

  protected int getOffset() {
    return myOffset;
  }

  @Override
  public boolean isBorderOpaque() {
    return false;
  }

  private static Color getGotItBorderColorStart(JComponent c) {
    if (isDefaultButton(c)) {
      return JBUI.CurrentTheme.Button.buttonOutlineColorStart(true);
    }
    if (isContrastGotIt(c)) {
      return JBUI.CurrentTheme.GotItTooltip.buttonBackgroundContrast();
    }
    return JBColor.namedColor("GotItTooltip.Button.startBorderColor",
                              JBUI.CurrentTheme.Button.buttonOutlineColorStart(false));
  }

  private static Color getGotItBorderColorEnd(JComponent c) {
    if (isDefaultButton(c)) {
      return JBUI.CurrentTheme.Button.buttonOutlineColorEnd(true);
    }
    if (isContrastGotIt(c)) {
      return JBUI.CurrentTheme.GotItTooltip.buttonBackgroundContrast();
    }
    return JBColor.namedColor("GotItTooltip.Button.endBorderColor",
                              JBUI.CurrentTheme.Button.buttonOutlineColorEnd(false));
  }
}
