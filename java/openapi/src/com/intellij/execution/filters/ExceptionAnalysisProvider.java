// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.filters;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Experimental
public interface ExceptionAnalysisProvider {
  /**
   * @param anchor a place (leaf element) in the source code where exception occurred
   * @param info exception info
   * @return an action to provide additional analysis for given exception; null if not available.
   */
  @Nullable AnAction getAnalysisAction(@NotNull PsiElement anchor, @NotNull ExceptionInfo info);

  /**
   * @param anchor a place (method name identifier) in the source code where next stack frame row is invoked 
   * @return an action to provide additional analysis for given location; null if not available
   */
  @Nullable AnAction getIntermediateRowAnalysisAction(@NotNull PsiElement anchor);
}
