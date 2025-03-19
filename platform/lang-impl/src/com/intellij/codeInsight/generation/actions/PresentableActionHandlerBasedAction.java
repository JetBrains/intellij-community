// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.generation.actions;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.actions.BaseCodeInsightAction;
import com.intellij.lang.ContextAwareActionHandler;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageCodeInsightActionHandler;
import com.intellij.lang.LanguageExtension;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtilCore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class PresentableActionHandlerBasedAction extends BaseCodeInsightAction {
  private @NlsContexts.Command String myCurrentActionName = null;

  @Override
  protected String getCommandName() {
    String actionName = myCurrentActionName;
    return actionName != null ? myCurrentActionName : super.getCommandName();
  }

  @Override
  public void update(@NotNull AnActionEvent event) {
    if (!getLanguageExtension().hasAnyExtensions()) {
      event.getPresentation().setEnabledAndVisible(false);
      return;
    }
    // since previous handled may have changed the presentation, we need to restore it; otherwise it will stick.
    event.getPresentation().copyFrom(getTemplatePresentation());
    applyTextOverride(event);
    super.update(event);

    // for Undo to show the correct action name, we remember it here to return from getCommandName(), which lack context of AnActionEvent
    myCurrentActionName = event.getPresentation().getText();
  }

  @Override
  protected void update(@NotNull Presentation presentation, @NotNull Project project,
                        @NotNull Editor editor, @NotNull PsiFile file, @NotNull DataContext dataContext, @Nullable String actionPlace) {
    // avoid evaluating isValidFor several times unnecessary

    CodeInsightActionHandler handler = getValidHandler(editor, file);
    presentation.setEnabled(handler != null);
    if (handler instanceof ContextAwareActionHandler && !ActionPlaces.isMainMenuOrActionSearch(actionPlace)) {
      presentation.setVisible(((ContextAwareActionHandler)handler).isAvailableForQuickList(editor, file, dataContext));
    }

    if (presentation.isVisible() && handler instanceof PresentableCodeInsightActionHandler) {
      ((PresentableCodeInsightActionHandler)handler).update(editor, file, presentation, actionPlace);
    }
  }

  @Override
  protected boolean isValidForFile(@NotNull Project project, @NotNull Editor editor, final @NotNull PsiFile file) {
    return getValidHandler(editor, file) != null;
  }

  private @Nullable CodeInsightActionHandler getValidHandler(@NotNull Editor editor, @NotNull PsiFile file) {
    Language language = PsiUtilCore.getLanguageAtOffset(file, editor.getCaretModel().getOffset());
    final CodeInsightActionHandler codeInsightActionHandler = getLanguageExtension().forLanguage(language);
    if (codeInsightActionHandler != null) {
      if (codeInsightActionHandler instanceof LanguageCodeInsightActionHandler) {
        if (((LanguageCodeInsightActionHandler)codeInsightActionHandler).isValidFor(editor, file)) {
          return codeInsightActionHandler;
        }
      }
      else {
        return codeInsightActionHandler;
      }
    }
    return null;
  }

  protected abstract @NotNull LanguageExtension<? extends CodeInsightActionHandler> getLanguageExtension();
}