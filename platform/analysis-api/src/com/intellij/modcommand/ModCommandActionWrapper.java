// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.modcommand;

import com.intellij.codeInsight.intention.FileModifier;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.IntentionActionDelegate;
import com.intellij.codeInsight.intention.PriorityAction;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record ModCommandActionWrapper(@NotNull ModCommandAction action) implements IntentionAction, PriorityAction {
  @Override
  public @Nullable PsiElement getElementToMakeWritable(@NotNull PsiFile currentFile) {
    return null;
  }

  @Override
  public @Nullable FileModifier getFileModifierForPreview(@NotNull PsiFile target) {
    return null;
  }

  @Override
  public @NotNull String getText() {
    return action.getName();
  }

  @Override
  public @NotNull String getFamilyName() {
    return action.getFamilyName();
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return action.isAvailable(ModCommandAction.ActionContext.from(editor, file));
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    ModCommand command = ModCommand.retrieve(() -> action.perform(ModCommandAction.ActionContext.from(editor, file)));
    if (command.prepare() != ModStatus.SUCCESS) return;
    command.execute(project);
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @Override
  public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    return action.generatePreview(ModCommandAction.ActionContext.from(editor, file));
  }

  @Override
  public @NotNull Priority getPriority() {
    return action.getPriority();
  }

  public static @Nullable ModCommandAction unwrap(@NotNull IntentionAction action) {
    while (action instanceof IntentionActionDelegate delegate) {
      action = delegate.getDelegate();
    }
    return action instanceof ModCommandActionWrapper wrapper ? wrapper.action() : null;
  }
}
