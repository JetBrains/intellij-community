// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui;

import com.intellij.openapi.ui.GraphicsConfig;
import com.intellij.util.ui.GraphicsUtil;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public abstract class RoundedActionButton extends JButton {

  private final int myHGap = JBUI.scale(4);
  private final int myTopBottomBorder;
  private final int myLeftRightBorder;

  public RoundedActionButton(int topBottomBorder, int leftRightBorder) {
    setOpaque(false);
    setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    myTopBottomBorder = JBUI.scale(topBottomBorder);
    myLeftRightBorder = JBUI.scale(leftRightBorder);
  }

  @Override
  public Dimension getPreferredSize() {
    int iconSize = getIcon().getIconWidth();
    final FontMetrics metrics = getFontMetrics(getFont());
    final int textWidth = metrics.stringWidth(getText());
    final int width = myLeftRightBorder + iconSize + myHGap + textWidth + myLeftRightBorder;
    final int height = myTopBottomBorder + Math.max(iconSize, metrics.getHeight()) + myTopBottomBorder;
    return new Dimension(width, height);
  }

  @Override
  public void paint(Graphics g2) {
    int iconSize = getIcon().getIconWidth();
    final Graphics2D g = (Graphics2D)g2;
    final GraphicsConfig config = GraphicsUtil.setupAAPainting(g);
    final int w = g.getClipBounds().width;
    final int h = g.getClipBounds().height;

    int borderArc = JBUI.scale(7);
    int border = JBUI.scale(1);
    int buttonArc = borderArc - border;

    g.setPaint(getBackgroundBorderPaint());
    g.fillRoundRect(0, 0, w, h, borderArc, borderArc);

    g.setPaint(getBackgroundPaint());
    g.fillRoundRect(border, border, w - 2 * border, h - 2 * border, buttonArc, buttonArc);

    g.setColor(getButtonForeground());
    g.drawString(getText(), myLeftRightBorder + iconSize + myHGap, getBaseline(w, h));
    getIcon().paintIcon(this, g, myLeftRightBorder, (getHeight() - getIcon().getIconHeight()) / 2);
    config.restore();
  }

  @NotNull
  protected abstract Paint getBackgroundBorderPaint();

  @NotNull
  protected abstract Paint getBackgroundPaint();

  @NotNull
  protected abstract Color getButtonForeground();
}
