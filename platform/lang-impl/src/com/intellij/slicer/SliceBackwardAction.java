// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.slicer;

import com.intellij.codeInsight.actions.CodeInsightAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

public final class SliceBackwardAction extends CodeInsightAction {
  @Override
  protected @NotNull SliceHandler getHandler() {
    return SliceHandler.create(true);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    if (LanguageSlicing.hasAnyProviders()) super.update(e);
    else e.getPresentation().setEnabledAndVisible(false);
  }

  @Override
  protected boolean isValidForFile(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    if (LanguageSlicing.getProvider(file) == null) {
      return false;
    }
    PsiElement expression = getHandler().getExpressionAtCaret(editor, file);
    return expression != null;
  }
}
