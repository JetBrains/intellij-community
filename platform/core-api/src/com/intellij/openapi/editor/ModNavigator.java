// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
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
  @Contract(pure = true)
  @NotNull Document getDocument();

  /**
   * @return the PsiFile associated with the document being edited
   */
  @Contract(pure = true)
  @NotNull PsiFile getPsiFile();

  /**
   * @return the current project
   */
  @Contract(pure = true)
  @NotNull Project getProject();

  /**
   * Registers a tab out scope, so pressing the tab inside the scope moves the caret to the specified offset
   * instead of adding a tab character.
   * <p> 
   * May do nothing if tab out is not supported by the implementation.
   * 
   * @param range scope range 
   * @param tabOutOffset target offset for tab-out
   */
  default void registerTabOut(@NotNull TextRange range, int tabOutOffset) {
    
  }
}
