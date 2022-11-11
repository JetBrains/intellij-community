// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.laf.darcula.ui;

import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.ex.ComboBoxAction;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import java.awt.*;
import java.awt.geom.Path2D;

public class ComboBoxButtonUI extends DarculaButtonUI {

  public static ComponentUI createUI(JComponent c) {
    return new ComboBoxButtonUI();
  }

  @Override
  public void paint(Graphics g, JComponent c) {
    super.paint(g, c);

    if (!(c instanceof ComboBoxAction.ComboBoxButton)) return;
    ComboBoxAction.ComboBoxButton button = (ComboBoxAction.ComboBoxButton)c;

    if (!button.isArrowVisible()) {
      return;
    }

    if (UIUtil.isUnderWin10LookAndFeel()) {
      Icon icon = ComboBoxAction.getArrowIcon(button.isEnabled());
      int x = button.getWidth() - icon.getIconWidth() - button.getInsets().right - button.getMargin().right - JBUIScale.scale(3) + button.getArrowGap();
      int y = (button.getHeight() - icon.getIconHeight()) / 2;
      icon.paintIcon(null, g, x, y);
    }
    else {
      Graphics2D g2 = (Graphics2D)g.create();
      try {
        int iconSize = JBUIScale.scale(16);
        int x = button.getWidth() - iconSize - button.getInsets().right - button.getMargin().right + button.getArrowGap(); // Different icons correction
        int y = (button.getHeight() - iconSize)/2;

        g2.translate(x, y);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_NORMALIZE);

        g2.setColor(JBUI.CurrentTheme.Arrow.foregroundColor(button.isEnabled()));

        Path2D arrow = new Path2D.Float(Path2D.WIND_EVEN_ODD);
        arrow.moveTo(JBUIScale.scale(3.5f), JBUIScale.scale(6f));
        arrow.lineTo(JBUIScale.scale(12.5f), JBUIScale.scale(6f));
        arrow.lineTo(JBUIScale.scale(8f), JBUIScale.scale(11f));
        arrow.closePath();

        g2.fill(arrow);
      }
      finally {
        g2.dispose();
      }
    }
  }

  @Override
  public Dimension getMinimumSize(JComponent c) {
    return new Dimension(super.getMinimumSize(c).width, getPreferredSize(c).height);
  }

  @Override
  protected Dimension getDarculaButtonSize(JComponent c, Dimension prefSize) {
    if (!(c instanceof ComboBoxAction.ComboBoxButton)) return prefSize;
    ComboBoxAction.ComboBoxButton button = (ComboBoxAction.ComboBoxButton)c;

    Dimension buttonSize = super.getDarculaButtonSize(c, prefSize);
    Insets i = button.getInsets();
    int width = buttonSize.width + (StringUtil.isNotEmpty(button.getText()) ? button.getIconTextGap() : 0) +
                (!button.isArrowVisible() ? 0 : JBUIScale.scale(16));

    int height = ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE.height + i.top + i.bottom;
    if (!button.isSmallVariant()) {
      height = Math.max(height, buttonSize.height);
    }
    Dimension size = new Dimension(width, height);
    JBInsets.addTo(size, button.getMargin());
    return size;
  }
}
