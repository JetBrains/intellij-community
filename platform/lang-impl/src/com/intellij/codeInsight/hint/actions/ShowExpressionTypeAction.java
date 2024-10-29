// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.hint.actions;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.actions.BaseCodeInsightAction;
import com.intellij.codeInsight.hint.ShowExpressionTypeHandler;
import com.intellij.lang.Language;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.ui.accessibility.ScreenReader;
import org.jetbrains.annotations.NotNull;

import java.awt.event.KeyEvent;

public final class ShowExpressionTypeAction extends BaseCodeInsightAction implements DumbAware {
  private boolean myRequestFocus = false;

  public ShowExpressionTypeAction() {
    setEnabledInModalContext(true);
  }

  @Override
  protected @NotNull CodeInsightActionHandler getHandler() {
    return new ShowExpressionTypeHandler(myRequestFocus);
  }

  @Override
  protected boolean isValidForFile(@NotNull Project project, @NotNull Editor editor, final @NotNull PsiFile file) {
    Language language = PsiUtilCore.getLanguageAtOffset(file, editor.getCaretModel().getOffset());
    return !ShowExpressionTypeHandler.getHandlers(project, language, file.getViewProvider().getBaseLanguage()).isEmpty();
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    // The tooltip gets the focus if using a screen reader and invocation through a keyboard shortcut.
    myRequestFocus = ScreenReader.isActive() && (e.getInputEvent() instanceof KeyEvent);
    super.actionPerformed(e);
  }

}