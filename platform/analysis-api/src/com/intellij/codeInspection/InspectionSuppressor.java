// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.openapi.project.PossiblyDumbAware;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Allows programmatic suppression of specific inspections.
 * <p>
 * Register via {@code com.intellij.lang.inspectionSuppressor} extension point.
 */
public interface InspectionSuppressor extends PossiblyDumbAware {
  /**
   * @see CustomSuppressableInspectionTool#isSuppressedFor(PsiElement)
   */
  boolean isSuppressedFor(@NotNull PsiElement element, @NotNull String toolId);

  /**
   * @see BatchSuppressableTool#getBatchSuppressActions(PsiElement)
   */
  SuppressQuickFix @NotNull [] getSuppressActions(@Nullable PsiElement element, @NotNull String toolId);
}
