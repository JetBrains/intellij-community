// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.generation.actions;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.actions.BaseCodeInsightAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import org.jetbrains.annotations.NotNull;

public class GenerateSuperMethodCallAction extends BaseCodeInsightAction implements DumbAware {
  @Override
  protected @NotNull CodeInsightActionHandler getHandler() {
    return new GenerateSuperMethodCallHandler();
  }

  @Override
  protected boolean isValidForFile(@NotNull Project project, @NotNull Editor editor, final @NotNull PsiFile psiFile) {
    if (!(psiFile instanceof PsiJavaFile)) {
      return false;
    }
    return DumbService.getInstance(project).computeWithAlternativeResolveEnabled(
      () -> GenerateSuperMethodCallHandler.canInsertSuper(editor, psiFile) != null);
  }
}