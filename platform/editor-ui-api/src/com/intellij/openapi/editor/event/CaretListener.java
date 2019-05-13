// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.event;

import org.jetbrains.annotations.NotNull;

import java.util.EventListener;

/**
 * Allows to receive notifications about caret movement, and caret additions/removal
 *
 * @see com.intellij.openapi.editor.CaretModel#addCaretListener(CaretListener)
 * @see EditorEventMulticaster#TOPIC
 */
public interface CaretListener extends EventListener {
  /**
   * Called when the caret position has changed.
   *
   * @param e the event containing information about the caret movement.
   */
  default void caretPositionChanged(@NotNull CaretEvent event) {
  }

  /**
   * Called when a new caret was added to the document.
   */
  default void caretAdded(@NotNull CaretEvent event) {
  }

  /**
   * Called when a caret was removed from the document.
   */
  default void caretRemoved(@NotNull CaretEvent event) {
  }
}
