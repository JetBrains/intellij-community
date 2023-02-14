// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.filters;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Allows to refine the stacktrace navigation position based on the previous line 
 */
@ApiStatus.Experimental
public interface ExceptionLineRefiner {
  /**
   * @param element element to check
   * @return element to position caret to if element matches; null if it doesn't match
   */
  @Nullable PsiElement matchElement(@NotNull PsiElement element);

  /**
   * @return ExceptionInfo object associated with this refiner if it matches some exception
   */
  default @Nullable ExceptionInfo getExceptionInfo() {
    return null;
  }

  /**
   * Provides a way to merge several lines into single refiner.
   * 
   * @param line next line
   * @return ExceptionLineRefiner if next line is successfully consumed; null otherwise
   */
  default @Nullable ExceptionLineRefiner consumeNextLine(String line) {
    return null;
  }
}
