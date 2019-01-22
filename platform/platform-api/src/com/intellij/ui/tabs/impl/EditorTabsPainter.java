// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.tabs.impl;

import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.ui.JBColor;
import com.intellij.ui.tabs.JBTabsPosition;
import com.intellij.util.ui.JBUI;

import java.awt.*;

public class EditorTabsPainter extends JBEditorTabsPainter {
  public EditorTabsPainter(JBEditorTabs tabs) {
    super(tabs);
  }

  @Override
  public void doPaintInactive(Graphics2D g2d,
                              Rectangle effectiveBounds,
                              int x,
                              int y,
                              int w,
                              int h,
                              Color tabColor,
                              int row,
                              int column,
                              boolean vertical) {
    fillRect(g2d, effectiveBounds, tabColor);
  }

  @Override
  public void doPaintBackground(Graphics2D g, Rectangle clip, boolean vertical, Rectangle rectangle) {
    g.setColor(getBackgroundColor());
    g.fill(clip);
  }

  @Override
  public void paintSelectionAndBorder(Graphics2D g2d, Rectangle rect, JBTabsImpl.ShapeInfo selectedShape, Insets insets, Color tabColor) {
    final JBTabsPosition position = myTabs.getPosition();
    /**TODO
     * тут рисуют подложку заселекшенного
     */

    fillRect(g2d, rect, tabColor);

    //TODO рисует подчеркивание
   // myEditorManager = FileEditorManager.getInstance(myTabs.proje);

    g2d.setColor(hasFocus(myTabs) ? JBUI.CurrentTheme.EditorTabs.underlineColor() : JBColor.gray);

    g2d.setColor(JBUI.CurrentTheme.EditorTabs.underlineColor());
    int thickness = 3;
    if (position == JBTabsPosition.bottom) {
      g2d.fillRect(rect.x, rect.y - 1, rect.width, thickness);
    } else if (position == JBTabsPosition.top){
      g2d.fillRect(rect.x, rect.y + rect.height - thickness + 1, rect.width, thickness);
    } else if (position == JBTabsPosition.left) {
      g2d.fillRect(rect.x + rect.width - thickness + 1, rect.y, thickness, rect.height);
    } else if (position == JBTabsPosition.right) {
      g2d.fillRect(rect.x, rect.y, thickness, rect.height);
    }
  }

  @Override
  public Color getBackgroundColor() {
    return JBUI.CurrentTheme.EditorTabs.backgroundColor();
  }
}
