// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui;

import com.intellij.openapi.ui.GraphicsConfig;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.GraphicsUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public abstract class RoundedActionButton extends JButton {

  private int myHGap = JBUIScale.scale(4);
  private final int myTopBottomBorder;
  private final int myLeftRightBorder;
  private int myArc = JBUIScale.scale(7);

  public RoundedActionButton(int topBottomBorder, int leftRightBorder) {
    setOpaque(false);
    setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    myTopBottomBorder = JBUIScale.scale(topBottomBorder);
    myLeftRightBorder = JBUIScale.scale(leftRightBorder);
  }

  @Override
  public Dimension getPreferredSize() {
    Icon icon = getIcon();
    int iconSize = icon == null ? 0 : icon.getIconWidth();
    final FontMetrics metrics = getFontMetrics(getFont());
    final int textWidth = metrics.stringWidth(getText());
    final int width = myLeftRightBorder + iconSize + myHGap + textWidth + myLeftRightBorder;
    final int height = myTopBottomBorder + Math.max(iconSize, metrics.getHeight()) + myTopBottomBorder;
    return new Dimension(width, height);
  }

  @Override
  public void paint(Graphics g2) {
    Icon icon = getIcon();
    int iconSize = icon == null ? 0 : icon.getIconWidth();
    final Graphics2D g = (Graphics2D)g2;
    final GraphicsConfig config = GraphicsUtil.setupAAPainting(g);
    final int w = g.getClipBounds().width;
    final int h = g.getClipBounds().height;

    int borderArc = myArc;
    int border = JBUIScale.scale(1);
    int buttonArc = borderArc - border;

    g.setPaint(getBackgroundBorderPaint());
    g.fillRoundRect(0, 0, w, h, borderArc, borderArc);

    g.setPaint(getBackgroundPaint());
    g.fillRoundRect(border, border, w - 2 * border, h - 2 * border, buttonArc, buttonArc);

    g.setColor(getButtonForeground());
    g.drawString(getText(), myLeftRightBorder + iconSize + myHGap, getBaseline(w, h));
    if (icon != null) {
      icon.paintIcon(this, g, myLeftRightBorder, (getHeight() - icon.getIconHeight()) / 2);
    }
    config.restore();
  }

  public int getArc() {
    return myArc;
  }

  public void setArc(int arc) {
    this.myArc = arc;
  }

  public int getHGap() {
    return myHGap;
  }

  public void setHGap(int hGap) {
    myHGap = hGap;
  }

  protected abstract @NotNull Paint getBackgroundBorderPaint();

  protected abstract @NotNull Paint getBackgroundPaint();

  protected abstract @NotNull Color getButtonForeground();
}
