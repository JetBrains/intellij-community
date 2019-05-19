// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui.laf.darcula.ui;

import com.intellij.openapi.ui.GraphicsConfig;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.Gray;
import com.intellij.util.ui.MenuItemLayoutHelper;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
public class DarculaRadioButtonMenuItemUI extends DarculaMenuItemUIBase {
  @SuppressWarnings({"MethodOverridesStaticMethodOfSuperclass", "UnusedDeclaration"})
  public static ComponentUI createUI(JComponent c) {
    return new DarculaRadioButtonMenuItemUI();
  }

  @Override
  protected String getPropertyPrefix() {
    return "RadioButtonMenuItem";
  }

  @Override
  protected void paintCheckIcon(Graphics g2, MenuItemLayoutHelper lh, MenuItemLayoutHelper.LayoutResult lr, Color holdc, Color foreground) {
    Graphics2D g = (Graphics2D)g2;
    final GraphicsConfig config = new GraphicsConfig(g);
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_DEFAULT);

    g.translate(lr.getCheckRect().x - 1, lr.getCheckRect().y - 1);

    int rad = 5;

    final int x = 0;
    final int y = 0;
    final int w = 13;
    final int h = 13;

    g.translate(x, y);

    //setup AA for lines
    Color bg = lh.getMenuItem().getBackground();
    g.setPaint(new GradientPaint(0, 0, ColorUtil.shift(bg, 1.5),
                                 0, 16, ColorUtil.shift(bg, 1.2)));

    g.fillOval(0, 1, w - 1, h - 1);

    g.setPaint(new GradientPaint(w / 2, 1, Gray._160.withAlpha(90), w / 2, h, Gray._100.withAlpha(90)));
    g.drawOval(0, 2, w - 1, h - 1);

    g.setPaint(Gray._40.withAlpha(200));
    g.drawOval(0, 1, w - 1, h - 1);

    if (lh.getMenuItem().isSelected()) {
      final boolean enabled = lh.getMenuItem().isEnabled();
      g.setColor(UIManager.getColor(enabled ? "RadioButton.darcula.selectionEnabledShadowColor"
                                            : "RadioButton.darcula.selectionDisabledShadowColor"));
      g.fillOval((w - rad) / 2, h / 2, rad, rad);
      g.setColor(UIManager.getColor(enabled ? "RadioButton.darcula.selectionEnabledColor"
                                            : "RadioButton.darcula.selectionDisabledColor"));
      g.fillOval((w - rad) / 2, h / 2 - 1, rad, rad);
    }
    config.restore();
    g.translate(-x, -y);


    g.translate(-lr.getCheckRect().x + 1, -lr.getCheckRect().y + 1);
    config.restore();
  }
}
