// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.impl.modcommand;

import com.intellij.codeInsight.intention.FileModifier;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.ModCommandAction;
import com.intellij.modcommand.ModCommandService;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class ModCommandActionQuickFixWrapper implements LocalQuickFix {
  private final ModCommandAction myAction;

  ModCommandActionQuickFixWrapper(@NotNull ModCommandAction action) {
    myAction = action;
  }

  ModCommandAction getAction() {
    return myAction;
  }

  @Override
  public @Nullable PsiElement getElementToMakeWritable(@NotNull PsiFile currentFile) {
    return null;
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @Override
  public @Nullable FileModifier getFileModifierForPreview(@NotNull PsiFile target) {
    return null;
  }

  @Override
  public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull ProblemDescriptor previewDescriptor) {
    return myAction.generatePreview(ModCommandAction.ActionContext.from(previewDescriptor));
  }

  @Override
  public @NotNull String getFamilyName() {
    return myAction.getFamilyName();
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    ModCommand command = myAction.perform(ModCommandAction.ActionContext.from(descriptor));
    ModCommandService.getInstance().execute(project, command);
  }
}
