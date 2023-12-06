// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.navigation.actions;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.generation.actions.PresentableActionHandlerBasedAction;
import com.intellij.lang.CodeInsightActions;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageExtension;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtilCore;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public final class GotoSuperAction extends PresentableActionHandlerBasedAction implements CodeInsightActionHandler, DumbAware {

  @NonNls public static final String FEATURE_ID = "navigation.goto.super";

  @NotNull
  @Override
  protected CodeInsightActionHandler getHandler() {
    return this;
  }

  @Override
  public void invoke(@NotNull final Project project, @NotNull final Editor editor, @NotNull final PsiFile file) {
    int offset = editor.getCaretModel().getOffset();
    final Language language = PsiUtilCore.getLanguageAtOffset(file, offset);

    final CodeInsightActionHandler codeInsightActionHandler = CodeInsightActions.GOTO_SUPER.forLanguage(language);
    if (codeInsightActionHandler != null) {
      DumbService.getInstance(project).withAlternativeResolveEnabled(() -> codeInsightActionHandler.invoke(project, editor, file));
    }
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @NotNull
  @Override
  protected LanguageExtension<CodeInsightActionHandler> getLanguageExtension() {
    return CodeInsightActions.GOTO_SUPER;
  }
}
