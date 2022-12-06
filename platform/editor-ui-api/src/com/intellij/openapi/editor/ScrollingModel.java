// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.event.VisibleAreaListener;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/**
 * Provides services for getting the visible area of the editor and scrolling the editor.
 *
 * @see Editor#getScrollingModel()
 */
public interface ScrollingModel {
  @NotNull
  Rectangle getVisibleArea();
  @NotNull
  Rectangle getVisibleAreaOnScrollingFinished();

  void scrollToCaret(@NotNull ScrollType scrollType);
  void scrollTo(@NotNull LogicalPosition pos, @NotNull ScrollType scrollType);

  void runActionOnScrollingFinished(@NotNull Runnable action);
  void disableAnimation();
  void enableAnimation();

  int getVerticalScrollOffset();
  int getHorizontalScrollOffset();

  void scrollVertically(int scrollOffset);
  void scrollHorizontally(int scrollOffset);
  default void centerHorizontally(@NotNull LogicalPosition pos) {}
  void scroll(int horizontalOffset, int verticalOffset);

  void addVisibleAreaListener(@NotNull VisibleAreaListener listener);
  void removeVisibleAreaListener(@NotNull VisibleAreaListener listener);
  default void addVisibleAreaListener(@NotNull VisibleAreaListener listener, @NotNull Disposable disposable) {
    addVisibleAreaListener(listener);
    Disposer.register(disposable, () -> removeVisibleAreaListener(listener));
  }

  interface Supplier {
    @NotNull Editor getEditor();
    @NotNull JScrollPane getScrollPane();
    @NotNull ScrollingHelper getScrollingHelper();
  }

  interface ScrollingHelper {
    @NotNull Point calculateScrollingLocation(@NotNull Editor editor, @NotNull VisualPosition pos);
    @NotNull Point calculateScrollingLocation(@NotNull Editor editor, @NotNull LogicalPosition pos);
  }
}
