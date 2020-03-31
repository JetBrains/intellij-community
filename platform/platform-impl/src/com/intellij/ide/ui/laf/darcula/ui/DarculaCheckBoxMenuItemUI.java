// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui.laf.darcula.ui;

import com.intellij.openapi.ui.GraphicsConfig;
import com.intellij.ui.Gray;
import com.intellij.util.ui.MenuItemLayoutHelper;
import com.intellij.util.ui.StartupUiUtil;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
public class DarculaCheckBoxMenuItemUI extends DarculaMenuItemUIBase {

  @SuppressWarnings({"MethodOverridesStaticMethodOfSuperclass", "UnusedDeclaration"})
  public static ComponentUI createUI(JComponent c) {
    return new DarculaCheckBoxMenuItemUI();
  }

  @Override
  protected String getPropertyPrefix() {
    return "CheckBoxMenuItem";
  }

  @Override
  protected void paintCheckIcon(Graphics g2, MenuItemLayoutHelper lh, MenuItemLayoutHelper.LayoutResult lr, Color holdc, Color foreground) {
    Graphics2D g = (Graphics2D)g2;
    final GraphicsConfig config = new GraphicsConfig(g);
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_DEFAULT);

    g.translate(lr.getCheckRect().x - 2, lr.getCheckRect().y);

    final int sz = 13;
    g.setPaint(new GradientPaint(sz / 2, 1, Gray._110, sz / 2, sz, Gray._95));
    g.fillRoundRect(0, 0, sz, sz - 1, 4, 4);

    g.setPaint(new GradientPaint(sz / 2, 1, Gray._120.withAlpha(0x5a), sz / 2, sz, Gray._105.withAlpha(90)));
    g.drawRoundRect(0, (StartupUiUtil.isUnderDarcula() ? 1 : 0), sz, sz - 1, 4, 4);

    g.setPaint(Gray._40.withAlpha(180));
    g.drawRoundRect(0, 0, sz, sz - 1, 4, 4);


    if (lh.getMenuItem().isSelected()) {
      g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
      g.setStroke(new BasicStroke(2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
      g.setPaint(Gray._30);
      g.drawLine(4, 7, 7, 10);
      g.drawLine(7, 10, sz, 2);
      g.setPaint(Gray._170);
      g.drawLine(4, 5, 7, 8);
      g.drawLine(7, 8, sz, 0);
    }

    g.translate(-lr.getCheckRect().x + 2, -lr.getCheckRect().y);
    config.restore();
    g.setColor(foreground);
  }
}
