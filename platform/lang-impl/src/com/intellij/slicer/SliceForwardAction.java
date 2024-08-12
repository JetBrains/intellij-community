// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.slicer;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.actions.CodeInsightAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

public final class SliceForwardAction extends CodeInsightAction {

  @Override
  protected @NotNull CodeInsightActionHandler getHandler() {
    return SliceHandler.create(false);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    if (LanguageSlicing.hasAnyProviders()) super.update(e);
    else e.getPresentation().setEnabledAndVisible(false);
  }

  @Override
  protected boolean isValidForFile(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    return LanguageSlicing.getProvider(file) != null;
  }
}