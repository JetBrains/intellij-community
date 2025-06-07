// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.codeInsight.intention.FileModifier;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.modcommand.ModCommandAction;
import com.intellij.modcommand.ModCommandService;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.List;

import static com.intellij.codeInsight.intention.CustomizableIntentionAction.RangeToHighlight;

/**
 * QuickFix based on {@link ProblemDescriptor ProblemDescriptor}
 * <p/>
 * N.B. Please DO NOT store PSI elements inside the LocalQuickFix instance to avoid holding too many PSI files during inspection.
 * Instead, use the {@link ProblemDescriptor#getPsiElement()}
 * in {@link QuickFix#applyFix(Project, CommonProblemDescriptor)}
 * to retrieve the PSI context the fix will work on.
 * See also {@link LocalQuickFixOnPsiElement} which uses {@link com.intellij.psi.SmartPsiElementPointer} instead of storing PSI elements.
 * <p>
 * Implement {@link com.intellij.openapi.util.Iconable Iconable} interface to
 * change icon in the quick-fix popup menu.
 * <p>
 * Implement {@link com.intellij.codeInsight.intention.HighPriorityAction HighPriorityAction} or
 * {@link com.intellij.codeInsight.intention.LowPriorityAction LowPriorityAction} to change ordering.
 * <p>
 * To learn how to implement preview correctly, read <a href="https://plugins.jetbrains.com/docs/intellij/code-intentions-preview.html">Intention Action Preview (IntelliJ Platform Docs)</a>.
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
   * @see <a href="https://plugins.jetbrains.com/docs/intellij/code-intentions-preview.html">Intention Action Preview (IntelliJ Platform Docs)</a>
   * @see <a href="https://www.jetbrains.com/help/inspectopedia/ActionIsNotPreviewFriendly.html">Field blocks intention preview (Inspectopedia)</a>
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

  /**
   * Highlight specified ranges when the quick-fix is selected in intention popup
   * 
   * @param project current project
   * @param descriptor problem descriptor
   * @return list of ranges to highlight, along with highlight attributes; empty list if no specific highlighting should be done
   */
  default @Unmodifiable @NotNull List<@NotNull RangeToHighlight> getRangesToHighlight(Project project, ProblemDescriptor descriptor) {
    return List.of();
  }

  /**
   * @return an array with a single element {@code fix} or an empty array if the argument is null
   */
  static @NotNull LocalQuickFix @NotNull [] notNullElements(@Nullable LocalQuickFix fix) {
    return fix == null ? EMPTY_ARRAY : new LocalQuickFix[]{fix};
  }
  /**
   * @return an array containing all not-null elements from {@code fixes}
   */
  static @NotNull LocalQuickFix @NotNull [] notNullElements(@Nullable LocalQuickFix @NotNull... fixes) {
    List<LocalQuickFix> result = new ArrayList<>(fixes.length);
    ContainerUtil.addAllNotNull(result, fixes);
    return result.isEmpty() ? EMPTY_ARRAY : result.toArray(EMPTY_ARRAY);
  }

  /**
   * @param action action to adapt to quick-fix
   * @return the action adapted to {@link LocalQuickFix} interface. The adapter is not perfect. In particular,
   * its {@link LocalQuickFix#getName()} simply returns the result of {@link #getFamilyName()}. If the client
   * of the quick-fix is ModCommand-aware, it can use {@link ModCommandService#unwrap(LocalQuickFix)} to get
   * this ModCommandAction back.
   */
  @Contract("null -> null; !null -> !null")
  static @Nullable LocalQuickFix from(@Nullable ModCommandAction action) {
    return action == null ? null : ModCommandService.getInstance().wrapToQuickFix(action);
  }
}
