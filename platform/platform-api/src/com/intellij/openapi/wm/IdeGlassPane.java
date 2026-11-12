// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.ui.Painter;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import kotlinx.coroutines.CoroutineScope;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.Component;
import java.awt.Cursor;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;

public interface IdeGlassPane {
  void addMousePreprocessor(@NotNull MouseListener listener, @NotNull Disposable parent);

  /**
   * Applicable for both MouseListener and MouseMotionListener.
   */
  void addMouseListener(@NotNull MouseListener listener, @NotNull CoroutineScope coroutineScope);

  void addMouseMotionPreprocessor(@NotNull MouseMotionListener listener, @NotNull Disposable parent);

  void addPainter(@Nullable Component component, @NotNull Painter painter, @NotNull Disposable parent);

  void setCursor(@Nullable Cursor cursor, @NotNull Object requestor);

  /**
   * Sets the {@code cursor} for the whole window of this glass pane, over the cursors of all components under the pane.
   * Use {@code null} to remove the override of the {@code requestor}.
   * If more than one requestor sets an override, the latest request wins.
   * The default implementation does nothing.
   *
   * @see IdeGlassPaneUtil#overrideCursorInAllWindows
   */
  @ApiStatus.Internal
  @RequiresEdt
  default void setCursorOverride(@Nullable Cursor cursor, @NotNull Object requestor) {
  }

  interface TopComponent {
    boolean canBePreprocessed(@NotNull MouseEvent e);
  }
}
