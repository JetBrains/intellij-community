// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * This interface must be used if you have some "custom" logic for batch fixes otherwise it is enough to implement equal {@link QuickFix#getFamilyName()} for the fixes.
 * <p>
 * If the fixes don't have same "family name" the "Inspect all" view doesn't show "fix all" option 
 * so same family name must be used even if you implement the {@link BatchQuickFix} interface.
 */
public interface BatchQuickFix {
  /**
   * Called to apply the cumulative fix. Is invoked in WriteAction when {@link QuickFix#startInWriteAction()} returns true
   *
   * @param project             {@link Project}
   * @param descriptors         problem reported by the tool on which fix should work
   * @param psiElementsToIgnore elements to be excluded from view during post-refresh
   * @param refreshViews        post-refresh inspection results view; would remove collected elements from the view
   */
  void applyFix(final @NotNull Project project,
                final CommonProblemDescriptor @NotNull [] descriptors,
                final @NotNull List<PsiElement> psiElementsToIgnore,
                final @Nullable Runnable refreshViews);
}
