// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.blockingCallsDetection;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

/**
 * Determines methods whose calls could block the execution thread.
 * <p>
 * Frameworks could implement this EP to provide such information based on framework-specific heuristics or markers.
 */
public interface BlockingMethodChecker {
  ExtensionPointName<BlockingMethodChecker> EP_NAME = ExtensionPointName.create("com.intellij.codeInsight.blockingMethodChecker");

  /**
   * @return true if current extension can detect blocking method in the given {@code file}
   */
  boolean isApplicable(@NotNull PsiFile file);

  default boolean isMethodBlocking(@NotNull MethodContext methodContext) {
    return false;
  }

  default boolean isMethodNonBlocking(@NotNull MethodContext methodContext) {
    return false;
  }

  /**
   * @param elementContext metadata that provides info about inspection settings and PsiElement (e.g. method call or reference)
   *                      which is located in "non-blocking" code fragment
   * @return empty array if one cannot provide any fixes, non-empty array of quick fixes otherwise
   */
  default LocalQuickFix @NotNull [] getQuickFixesFor(@NotNull ElementContext elementContext) {
    return LocalQuickFix.EMPTY_ARRAY;
  }
}
