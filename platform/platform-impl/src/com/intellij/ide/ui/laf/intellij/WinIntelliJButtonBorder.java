// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui.laf.intellij;

import com.intellij.ide.ui.laf.darcula.ui.DarculaButtonUI;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.plaf.UIResource;
import java.awt.*;
import java.awt.geom.Path2D;

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
    Rectangle outerRect = new Rectangle(x, y, width, height);
    try {
      JBInsets.removeFrom(outerRect, b.getInsets());

      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_NORMALIZE);

      Path2D border = new Path2D.Float(Path2D.WIND_EVEN_ODD);
      border.append(outerRect, false);

      Rectangle innerRect = new Rectangle(outerRect);
      JBInsets.removeFrom(innerRect, JBUI.insets(getBorderWidth(b)));
      border.append(innerRect, false);

      g2.setColor(getBorderColor(b));
      if (!c.isEnabled()) {
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, DISABLED_ALPHA_LEVEL));
      }

      g2.fill(border);
    } finally {
      g2.dispose();
    }
  }

  private static Color getBorderColor(AbstractButton b) {
    ButtonModel bm = b.getModel();

    Color focusedBorderColor = (Color)b.getClientProperty("JButton.focusedBorderColor");
    if (bm.isPressed()) {
      return focusedBorderColor != null ?
             focusedBorderColor : UIManager.getColor("Button.intellij.native.pressedBorderColor");
    } else if (b.hasFocus() || bm.isRollover() || DarculaButtonUI.isDefaultButton(b)) {
      return focusedBorderColor != null ?
             focusedBorderColor : UIManager.getColor("Button.intellij.native.focusedBorderColor");
    } else {
      Color borderColor = (Color)b.getClientProperty("JButton.borderColor");
      return borderColor != null ? borderColor : UIManager.getColor("Button.intellij.native.borderColor");
    }
  }

  protected boolean isWideBorder(@NotNull AbstractButton b) {
    ButtonModel bm = b.getModel();
    return b.isEnabled() && !bm.isPressed() && !b.hasFocus() && !bm.isRollover() && DarculaButtonUI.isDefaultButton(b);
  }

  protected int getBorderWidth(@NotNull AbstractButton b) {
    return isWideBorder(b) ? 2 : 1;
  }

  @Override
  public Insets getBorderInsets(Component c) {
    return JBUI.insets(1);
  }

  @Override
  public boolean isBorderOpaque() {
    return false;
  }
}
