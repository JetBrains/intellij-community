// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions.fillParagraph;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.actions.BaseCodeInsightAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

/**
 * <p>
 * Action to re-flow paragraph to fit right margin.
 * Glues paragraph and then splits into lines with appropriate length
 * <p>
 * The action came from Emacs users // PY-4775
 */
public final class FillParagraphAction extends BaseCodeInsightAction implements DumbAware {
  @Override
  protected @NotNull CodeInsightActionHandler getHandler() {
    return new Handler();
  }

  private static final class Handler implements CodeInsightActionHandler {
    @Override
    public void invoke(final @NotNull Project project, final @NotNull Editor editor, final @NotNull PsiFile file) {
      ParagraphFillHandler paragraphFillHandler = LanguageFillParagraphExtension.INSTANCE.forLanguage(file.getLanguage());

      int offset = editor.getCaretModel().getOffset();
      PsiElement element = file.findElementAt(offset);
      if (element != null
          && paragraphFillHandler != null
          && paragraphFillHandler.isAvailableForFile(file)
          && paragraphFillHandler.isAvailableForElement(element)) {
        paragraphFillHandler.performOnElement(element, editor);
      }
    }
  }

  @Override
  protected boolean isValidForFile(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    ParagraphFillHandler handler = LanguageFillParagraphExtension.INSTANCE.forLanguage(file.getLanguage());
    return handler != null && handler.isAvailableForFile(file);
  }
}
