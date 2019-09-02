// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Allows programmatic suppression of specific inspections.
 * <p>
 * Register via {@code com.intellij.lang.inspectionSuppressor} extension point.
 */
public interface InspectionSuppressor {
  /**
   * @see CustomSuppressableInspectionTool#isSuppressedFor(PsiElement)
   */
  boolean isSuppressedFor(@NotNull PsiElement element, @NotNull String toolId);

  /**
   * @see BatchSuppressableTool#getBatchSuppressActions(PsiElement)
   */
  @NotNull
  SuppressQuickFix[] getSuppressActions(@Nullable PsiElement element, @NotNull String toolId);
}
