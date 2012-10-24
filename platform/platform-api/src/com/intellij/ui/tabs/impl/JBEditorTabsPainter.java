package com.intellij.ui.tabs.impl;

import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
interface JBEditorTabsPainter {
  void doPaintInactive(Graphics2D g2d, Rectangle effectiveBounds, int x, int y, int w, int h, Color tabColor);

  void doPaintBackground(Graphics2D g, Rectangle clip, boolean vertical, Rectangle rectangle);

  void paintSelectionAndBorder(Graphics2D g2d,
                               Rectangle rect,
                               JBTabsImpl.ShapeInfo selectedShape,
                               Insets insets,
                               Color tabColor,
                               boolean horizontalTabs);

  Color getBackgroundColor();
}
