// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.codeInsight.intention.FileModifier;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;

/**
 * QuickFix based on {@link ProblemDescriptor ProblemDescriptor}
 * <p/>
 * N.B. Please DO NOT store PSI elements inside the LocalQuickFix instance, to avoid holding too much PSI files during inspection.
 * Instead, use the {@link ProblemDescriptor#getPsiElement()}
 * in {@link QuickFix#applyFix(Project, CommonProblemDescriptor)}
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

  /**
   * Try to apply this fix for non-physical file to display the preview. This method is called outside write action,
   * even if {@link #startInWriteAction()} returns true. It's not allowed to modify
   * any physical PSI or spawn any actions in other threads within this method. This method may behave differently than
   * {@link #applyFix(Project, CommonProblemDescriptor)} method. In particular, changes in other files or user interactions
   * like renaming the created variable should not be performed by this method.
   * <p>
   * Default implementation calls {@link #getFileModifierForPreview(PsiFile)} and {@link #applyFix(Project, CommonProblemDescriptor)}
   * on the result. This might fail if the original quick-fix is not prepared for preview. In this case,
   * overriding {@code getFileModifierForPreview} or {@code applyFixForPreview} is desired.
   *
   * @param project current project
   * @param previewDescriptor problem descriptor which refers to the non-physical file copy where the fix should be applied
   * @return true if the fix was successfully applied to the copy; false otherwise
   * @deprecated do not call or override this method: this API will be changed.
   */
  @Deprecated(forRemoval = true)
  default boolean applyFixForPreview(@NotNull Project project, @NotNull ProblemDescriptor previewDescriptor) {
    if (!startInWriteAction()) return false;
    PsiElement element = previewDescriptor.getStartElement();
    if (element == null) return false;
    PsiFile file = element.getContainingFile();
    LocalQuickFix fix = ObjectUtils.tryCast(getFileModifierForPreview(file), LocalQuickFix.class);
    if (fix == null || fix.getElementToMakeWritable(file) != file) return false;
    fix.applyFix(project, previewDescriptor);
    return true;
  }

  /**
   * Generate preview for this fix. This method is called outside write action,
   * even if {@link #startInWriteAction()} returns true. It's not allowed to modify
   * any physical PSI or spawn any actions in other threads within this method. 
   * <p>
   * There are several possibilities to make the preview:
   * <ul>
   *   <li>Apply changes to file referred from {@code previewDescriptor}, then return {@link IntentionPreviewInfo#DIFF}. The
   *   file referred from {@code previewDescriptor} is a non-physical copy of the original file.</li>
   *   <li>Return {@link IntentionPreviewInfo.Html} object to display custom HTML</li>
   *   <li>Return {@link IntentionPreviewInfo#EMPTY} to generate no preview at all</li>
   * </ul>
   * <p>
   * Default implementation calls {@link #getFileModifierForPreview(PsiFile)} and {@link #applyFix(Project, CommonProblemDescriptor)}
   * on the result. This might fail if the original quick-fix is not prepared for preview. In this case,
   * overriding {@code getFileModifierForPreview} or {@code generatePreview} is desired.
   *
   * @param project current project
   * @param previewDescriptor problem descriptor which refers to the non-physical file copy where the fix should be applied
   * @return an object that describes the action preview to display
   */
  default @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull ProblemDescriptor previewDescriptor) {
    if (!startInWriteAction()) return IntentionPreviewInfo.EMPTY;
    PsiElement element = previewDescriptor.getStartElement();
    if (element == null) return IntentionPreviewInfo.EMPTY;
    PsiFile file = element.getContainingFile();
    LocalQuickFix fix = ObjectUtils.tryCast(getFileModifierForPreview(file), LocalQuickFix.class);
    if (fix == null || fix.getElementToMakeWritable(file) != file) return IntentionPreviewInfo.EMPTY;
    fix.applyFix(project, previewDescriptor);
    return IntentionPreviewInfo.DIFF;
  }
}
