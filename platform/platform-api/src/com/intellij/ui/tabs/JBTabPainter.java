// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.tabs;

import com.intellij.ui.tabs.impl.JBDefaultTabPainter;
import com.intellij.ui.tabs.impl.JBEditorTabPainter;

import java.awt.*;

public interface JBTabPainter {
  enum PainterType {
    EDITOR,
    DEFAULT,
    TOOL_WINDOW
  }

  JBEditorTabPainter editorPainter = new JBEditorTabPainter();
  JBTabPainter toolWindowPainter = new JBDefaultTabPainter(TabTheme.Companion.getTOOLWINDOW_TAB());
  JBTabPainter defaultPainter = new JBDefaultTabPainter();

  Color getBackgroundColor();

 void paintBorderLine(Graphics2D g, int thickness, Point from, Point to);

  void fillBackground(Graphics2D g, Rectangle rect);

  void paintTab(JBTabsPosition position, Graphics2D g, Rectangle bounds, int borderThickness, Color tabColor, Boolean hovered);

  void paintSelectedTab(JBTabsPosition position, Graphics2D g, Rectangle rect, Color tabColor, Boolean active, Boolean hovered);
}

