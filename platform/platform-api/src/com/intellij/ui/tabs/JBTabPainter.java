// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.tabs;

import com.intellij.ui.tabs.impl.JBDefaultTabPainter;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

public interface JBTabPainter {
  enum PainterType {
    EDITOR,
    DEFAULT,
    TOOL_WINDOW
  }

  JBTabPainter editorPainter = new JBDefaultTabPainter(TabTheme.Companion.getEDITOR_TAB());
  JBTabPainter toolWindowPainter = new JBDefaultTabPainter(TabTheme.Companion.getTOOLWINDOW_TAB());
  JBTabPainter defaultPainter = new JBDefaultTabPainter();

  static JBTabPainter getInstance(@Nullable PainterType type) {
    if(type == null) return defaultPainter;

    switch (type) {
      case EDITOR:
        return editorPainter;
      case TOOL_WINDOW:
        return toolWindowPainter;
      default:
        return defaultPainter;
    }
  }


  // @Deprecated("You should move the painting logic to an implementation of this interface")
  @Deprecated
  Color getBackgroundColor();

  int getBorderThickness();

  void paintBorders(Graphics2D g, Rectangle bounds, JBTabsPosition position, int headerFitHeight, int rows, int yOffset);

  void fillBackground(Graphics2D g, Rectangle rect);

  void paintTab(Graphics2D g, Rectangle rect, Color tabColor, Boolean hovered);

  void paintSelectedTab(Graphics2D g, Rectangle rect, Color tabColor, JBTabsPosition position, Boolean active, Boolean hovered);
}

