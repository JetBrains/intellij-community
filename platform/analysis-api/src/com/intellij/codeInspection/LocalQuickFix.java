// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.codeInsight.intention.FileModifier;

/**
 * QuickFix based on {@link ProblemDescriptor ProblemDescriptor}
 * <p/>
 * N.B. Please DO NOT store PSI elements inside the LocalQuickFix instance, to avoid holding too much PSI files during inspection.
 * Instead, use the {@link ProblemDescriptor#getPsiElement()}
 * in {@link QuickFix#applyFix(com.intellij.openapi.project.Project, CommonProblemDescriptor)}
 * to retrieve the PSI context the fix will work on.
 * See also {@link LocalQuickFixOnPsiElement} which uses {@link com.intellij.psi.SmartPsiElementPointer} instead of storing PSI elements.
 * <p/>
 * Implement {@link com.intellij.openapi.util.Iconable Iconable} interface to
 * change icon in quick fix popup menu.
 * <p/>
 * Implement {@link com.intellij.codeInsight.intention.HighPriorityAction HighPriorityAction} or
 * {@link com.intellij.codeInsight.intention.LowPriorityAction LowPriorityAction} to change ordering.
 *
 * @author max
 * @see ProblemDescriptor
 * @see com.intellij.openapi.util.Iconable
 */
public interface LocalQuickFix extends QuickFix<ProblemDescriptor>, FileModifier {
  LocalQuickFix[] EMPTY_ARRAY = new LocalQuickFix[0];

  /**
   * @return true if this quick-fix should not be automatically filtered out when running inspections in the batch mode.
   * Fixes that require editor or display UI should return false. It's not harmful to return true if the fix is never
   * registered in the batch mode (e.g. {@link ProblemsHolder#isOnTheFly()} is checked at the fix creation site).
   */
  default boolean availableInBatchMode() {
    return true;
  }
}
