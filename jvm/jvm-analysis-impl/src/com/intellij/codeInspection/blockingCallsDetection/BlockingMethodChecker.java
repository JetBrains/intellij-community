// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.blockingCallsDetection;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Determines methods whose calls could block the execution thread.
 * <p>
 * Frameworks could implement this EP to provide such information based on framework-specific heuristics or markers.
 *
 * @since 2018.3
 */
@ApiStatus.Experimental
public interface BlockingMethodChecker {
  ExtensionPointName<BlockingMethodChecker> EP_NAME = ExtensionPointName.create("com.intellij.codeInsight.blockingMethodChecker");

  /**
   * @return true if current extension can detect blocking method in the given {@code file}
   */
  boolean isApplicable(@NotNull PsiFile file);

  boolean isMethodBlocking(@NotNull PsiMethod method);
}
