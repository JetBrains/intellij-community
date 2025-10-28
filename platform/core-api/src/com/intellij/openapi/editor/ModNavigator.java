// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor;

import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * An interface that provides navigation functionality for the editor.
 * Depending on the implementation, the commands can be directly applied to the editor
 * or can be remembered and executed later.
 */
@ApiStatus.Experimental
public interface ModNavigator {
  /**
   * Selects given range
   *
   * @param range range to select
   */
  void select(@NotNull TextRange range);

  /**
   * Navigates to a given offset
   *
   * @param offset offset to move to
   */
  void moveCaretTo(int offset);

  /**
   * @return current caret offset
   */
  int getCaretOffset();

  /**
   * @return the document being edited 
   */
  @NotNull Document getDocument();

  /**
   * Registers a tab out scope, so pressing tab inside the scope moves the caret to the specified offset
   * instead of adding a tab character.
   * 
   * May do nothing if tab out is not supported by the implementation.
   * 
   * @param range scope range 
   * @param tabOutOffset target offset for tab-out
   */
  default void registerTabOut(@NotNull TextRange range, int tabOutOffset) {
    
  }
}
