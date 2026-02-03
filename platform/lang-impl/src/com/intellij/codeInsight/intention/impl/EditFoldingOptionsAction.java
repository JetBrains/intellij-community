// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.intention.impl;

import com.intellij.application.options.editor.CodeFoldingConfigurable;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.LowPriorityAction;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
@SuppressWarnings("IntentionDescriptionNotFoundInspection")
public final class EditFoldingOptionsAction implements IntentionAction, LowPriorityAction {
  @Override
  public @NotNull String getText() {
    return ApplicationBundle.message("edit.code.folding.options");
  }

  @Override
  public @NotNull String getFamilyName() {
    return getText();
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile psiFile) {
    return editor.getFoldingModel().isOffsetCollapsed(editor.getCaretModel().getOffset());
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile psiFile) throws IncorrectOperationException {
    ShowSettingsUtil.getInstance().showSettingsDialog(project, CodeFoldingConfigurable.class);
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }
}
