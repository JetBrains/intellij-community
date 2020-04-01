// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.codeInsight.intention.FileModifier;
import com.intellij.codeInspection.util.PreviewUtil;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
   * Returns the fix that could be applied to the non-physical copy of the file.
   * May return itself if the fix doesn't depend on the file.
   *
   * @param target target non-physical file 
   * @return the action that could be applied to the non-physical copy of the file.
   * Returns null if operation is not supported.
   */
  default @Nullable LocalQuickFix tryTransferFixToFile(@NotNull PsiFile target) {
    if (!startInWriteAction() || PreviewUtil.mayBeFileBound(this)) return null;
    // No PSI-specific state: it's safe to apply this fix to a file copy
    return this;
  }
}
