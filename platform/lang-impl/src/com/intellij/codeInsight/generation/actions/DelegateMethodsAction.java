// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.generation.actions;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.actions.BaseCodeInsightAction;
import com.intellij.codeInsight.generation.DelegateMethodsHandler;
import com.intellij.lang.CodeInsightActions;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageCodeInsightActionHandler;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtilCore;
import org.jetbrains.annotations.NotNull;

public final class DelegateMethodsAction extends BaseCodeInsightAction implements DumbAware {

  @Override
  protected @NotNull CodeInsightActionHandler getHandler() {
    return new DelegateMethodsHandler();
  }

  @Override
  protected boolean isValidForFile(@NotNull Project project, @NotNull Editor editor, final @NotNull PsiFile file) {
    Language language = PsiUtilCore.getLanguageAtOffset(file, editor.getCaretModel().getOffset());
    final LanguageCodeInsightActionHandler codeInsightActionHandler = CodeInsightActions.DELEGATE_METHODS.forLanguage(language);
    if (codeInsightActionHandler != null) {
      return codeInsightActionHandler.isValidFor(editor, file);
    }
    return false;
  }

  @Override
  public void update(final @NotNull AnActionEvent event) {
    if (CodeInsightActions.DELEGATE_METHODS.hasAnyExtensions()) {
      event.getPresentation().setVisible(true);
      super.update(event);
    }
    else {
      event.getPresentation().setVisible(false);
    }
  }
}