// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
   * @return element to position caret to (on the same line) and reason if the element matches; null if it doesn't match
   */
  @Nullable ExceptionLineRefiner.RefinerMatchResult matchElement(@NotNull PsiElement element);

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

  /**
   * @param target the closest psi element to the reason, which is placed on the line from a stacktrace
   * @param reason reason of exception
   * <p>
   * Example:
   * <p>
   * <pre>
   * {@code
   *
   * (line: 5)    public static void main(String[] args) {
   * (line: 6)        String text = null;
   * (line: 7)        text
   * (line: 8)                .
   * (line: 9)                trim();
   *              }
   * }
   * </pre>
   * Stacktrace: ClassName.main(ClassName.java:9) <p>
   * In this case, reason - <code>text</code>, target - <code>trim</code>
   */
  record RefinerMatchResult(@NotNull PsiElement target, @NotNull PsiElement reason){

    public static @Nullable ExceptionLineRefiner.RefinerMatchResult of(@Nullable PsiElement element) {
      if (element == null) return null;
      return new RefinerMatchResult(element, element);
    }
  }
}
