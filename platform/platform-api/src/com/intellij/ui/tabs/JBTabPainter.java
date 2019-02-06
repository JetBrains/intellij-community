// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.tabs;

import com.intellij.ui.tabs.impl.JBDefaultTabPainter;
import com.intellij.ui.tabs.impl.JBEditorTabPainter;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

public interface JBTabPainter {
  enum PainterType {
    EDITOR,
    DEFAULT,
    TOOL_WINDOW
  }

  JBTabPainter editorPainter = new JBEditorTabPainter();
  JBTabPainter defaultPainter = new JBDefaultTabPainter();

  static JBTabPainter getInstance(@Nullable PainterType type) {
    if(type == null) return defaultPainter;

    switch (type) {
      case EDITOR:
        return editorPainter;
      case TOOL_WINDOW:
      default:
        return defaultPainter;
    }
  }


  // @Deprecated("You should move the painting logic to an implementation of this interface")
  @Deprecated
  Color getBackgroundColor();

  int getBorderThickness();

  default void paintBorders(JBTabsPosition position, Graphics2D g, Rectangle bounds, int headerFitHeight, int rows, int yOffset) {};

  void fillBackground(Graphics2D g, Rectangle rect);

  void paintTab(JBTabsPosition position, Graphics2D g, Rectangle bounds, Color tabColor, Boolean hovered);

  void paintSelectedTab(JBTabsPosition position, Graphics2D g, Rectangle rect, Color tabColor, Boolean active, Boolean hovered);
}

