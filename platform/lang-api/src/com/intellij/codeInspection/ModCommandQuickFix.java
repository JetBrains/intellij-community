// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.codeInsight.intention.FileModifier;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.modcommand.*;
import com.intellij.model.SideEffectGuard;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.ExecutionException;

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
   * @see ModCommands for useful utility methods to construct the commands
   */
  @NotNull
  public abstract ModCommand perform(@NotNull Project project, @NotNull ProblemDescriptor descriptor);
  
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
    ModCommand command = getCommand(project, descriptor);
    if (command.prepare() != ModStatus.SUCCESS) return;
    command.execute(project);
  }

  private @NotNull ModCommand getCommand(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    ModCommand command;
    try {
      command = ReadAction.nonBlocking(() -> SideEffectGuard.computeWithoutSideEffects(() -> perform(project, descriptor)))
        .submit(AppExecutorUtil.getAppExecutorService()).get();
    }
    catch (InterruptedException | ExecutionException e) {
      throw new RuntimeException(e);
    }
    if (ContainerUtil.exists(command.unpack(), c -> c instanceof ModLegacyBridge)) {
      throw new AssertionError("Wrong fix implementation: " + getClass().getName());
    }
    return command;
  }

  @Override
  public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull ProblemDescriptor previewDescriptor) {
    IntentionPreviewInfo info = null;
    IntentionPreviewInfo info2 = IntentionPreviewInfo.EMPTY;
    for (ModCommand command : getCommand(project, previewDescriptor).unpack()) {
      if (command instanceof ModUpdatePsiFile modFile) {
        if (info != null) {
          return IntentionPreviewInfo.EMPTY;
        }
        PsiFile file = previewDescriptor.getPsiElement().getContainingFile();
        if (file == modFile.file()) {
          modFile.execute(project);
          info = IntentionPreviewInfo.DIFF;
        } else {
          info = new IntentionPreviewInfo.CustomDiff(modFile.file().getFileType(), modFile.file().getName(), modFile.oldText(),
                                                     modFile.newText());
        }
      }
      else if (command instanceof ModNavigate navigate && navigate.caret() != -1) {
        PsiFile file = PsiManager.getInstance(project).findFile(navigate.file());
        if (file != null) {
          info2 = IntentionPreviewInfo.navigate(file, navigate.caret());
        }
      }
    }
    return info == null ? info2 : info;
  }
}
