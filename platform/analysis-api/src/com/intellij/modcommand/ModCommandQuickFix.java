// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.modcommand;

import com.intellij.codeInsight.intention.FileModifier;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInsight.intention.preview.IntentionPreviewUtils;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A novel experimental kind of quick-fix that creates a command instead of performing actual modification.
 * Default preview for this fix is based on the command returned from {@link #perform(Project, ProblemDescriptor)}
 * @see ModCommand
 */
@ApiStatus.Experimental
public abstract class ModCommandQuickFix implements LocalQuickFix {
  /**
   * A method that computes the final action of the quick-fix and returns it. Executed in a background read-action.
   * 
   * @param project    {@link Project}
   * @param descriptor problem reported by the tool which provided this quick fix action
   * @return a command to be applied to finally execute the fix.
   */
  public abstract @NotNull ModCommand perform(@NotNull Project project, @NotNull ProblemDescriptor descriptor);
  
  @Override
  public final boolean startInWriteAction() {
    return false;
  }

  @Override
  public final @Nullable PsiElement getElementToMakeWritable(@NotNull PsiFile currentFile) {
    return null;
  }

  @Override
  public final @Nullable FileModifier getFileModifierForPreview(@NotNull PsiFile target) {
    return null;
  }

  @Override
  public final void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    ModCommand command = perform(project, descriptor);
    ModCommandExecutor.getInstance().executeInteractively(ActionContext.from(descriptor), command, null);
  }

  @Override
  public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull ProblemDescriptor previewDescriptor) {
    ModCommand modCommand = perform(project, previewDescriptor);
    return IntentionPreviewUtils.getModCommandPreview(modCommand, ActionContext.from(previewDescriptor));
  }
}
