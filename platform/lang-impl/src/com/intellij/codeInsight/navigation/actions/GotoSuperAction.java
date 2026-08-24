// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.navigation.actions;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.NavigationOptionsAwareCodeInsightActionHandler;
import com.intellij.codeInsight.generation.actions.PresentableActionHandlerBasedAction;
import com.intellij.lang.CodeInsightActions;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageExtension;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.platform.ide.navigation.NavigationOptions;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtilCore;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class GotoSuperAction extends PresentableActionHandlerBasedAction implements CodeInsightActionHandler, DumbAware {

  public static final @NonNls String FEATURE_ID = "navigation.goto.super";

  @Override
  protected @NotNull CodeInsightActionHandler getHandler() {
    return this;
  }

  @Override
  protected @NotNull CodeInsightActionHandler getHandler(@NotNull DataContext dataContext) {
    NavigationOptions options = NavigationOptions.fromContext(dataContext);
    return new CodeInsightActionHandler() {
      @Override
      public void invoke(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile psiFile) {
        GotoSuperAction.invoke(project, editor, psiFile, options);
      }

      @Override
      public boolean startInWriteAction() {
        return false;
      }
    };
  }

  @Override
  public void invoke(final @NotNull Project project, final @NotNull Editor editor, final @NotNull PsiFile psiFile) {
    invoke(project, editor, psiFile, NavigationOptions.requestFocus());
  }

  private static void invoke(@NotNull Project project,
                             @NotNull Editor editor,
                             @NotNull PsiFile psiFile,
                             @NotNull NavigationOptions options) {
    int offset = editor.getCaretModel().getOffset();
    final Language language = PsiUtilCore.getLanguageAtOffset(psiFile, offset);

    final CodeInsightActionHandler codeInsightActionHandler = CodeInsightActions.GOTO_SUPER.forLanguage(language);
    if (codeInsightActionHandler != null) {
      DumbService.getInstance(project).withAlternativeResolveEnabled(() -> {
        if (codeInsightActionHandler instanceof NavigationOptionsAwareCodeInsightActionHandler optionsAwareHandler) {
          optionsAwareHandler.invoke(project, editor, psiFile, options);
        }
        else {
          codeInsightActionHandler.invoke(project, editor, psiFile);
        }
      });
    }
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @Override
  protected @NotNull LanguageExtension<CodeInsightActionHandler> getLanguageExtension() {
    return CodeInsightActions.GOTO_SUPER;
  }
}
