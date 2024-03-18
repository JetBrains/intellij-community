// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.modcommand;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * An interface that provides navigation functionality for the editor.
 * Depending on the implementation, the commands can be directly applied to the editor,
 * or can be remembered and executed later.
 */
@ApiStatus.Experimental
public interface ModPsiNavigator {
  /**
   * Selects given element
   *
   * @param element element to select
   */
  void select(@NotNull PsiElement element);

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
   * Navigates to a given element
   *
   * @param element element to navigate to
   */
  void moveCaretTo(@NotNull PsiElement element);

  /**
   * @return current caret offset
   */
  int getCaretOffset();
}
