/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.editor;

import com.intellij.openapi.editor.event.VisibleAreaListener;

import java.awt.*;

public interface ScrollingModel {
  Rectangle getVisibleArea();
  Rectangle getVisibleAreaOnScrollingFinished();

  void scrollToCaret(ScrollType scrollType);
  void scrollTo(LogicalPosition pos, ScrollType scrollType);

  void runActionOnScrollingFinished(Runnable action);
  void disableAnimation();
  void enableAnimation();

  int getVerticalScrollOffset();
  int getHorizontalScrollOffset();

  void scrollVertically(int scrollOffset);
  void scrollHorizontally(int scrollOffset);

  void addVisibleAreaListener(VisibleAreaListener listener);
  void removeVisibleAreaListener(VisibleAreaListener listener);
}
