// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.editorActions;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.actions.BaseCodeInsightAction;
import com.intellij.codeInsight.editorActions.emacs.EmacsProcessingHandler;
import com.intellij.codeInsight.editorActions.emacs.LanguageEmacsExtension;
import com.intellij.lang.LanguageFormatting;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;

public final class EmacsStyleIndentAction extends BaseCodeInsightAction implements DumbAware {
  @Override
  protected @NotNull CodeInsightActionHandler getHandler() {
    return new Handler();
  }

  @Override
  protected boolean isValidForFile(final @NotNull Project project, final @NotNull Editor editor, final @NotNull PsiFile file) {
    PsiElement context = ObjectUtils.notNull(file.findElementAt(editor.getCaretModel().getOffset()), file);
    return LanguageFormatting.INSTANCE.forContext(context) != null;
  }

  private static final class Handler implements CodeInsightActionHandler {
    @Override
    public void invoke(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
      EmacsProcessingHandler emacsProcessingHandler = LanguageEmacsExtension.INSTANCE.forLanguage(file.getLanguage());
      if (emacsProcessingHandler != null) {
        EmacsProcessingHandler.Result result = emacsProcessingHandler.changeIndent(project, editor, file);
        if (result == EmacsProcessingHandler.Result.STOP) {
          return;
        }
      }

      Document document = editor.getDocument();
      int startLine = document.getLineNumber(editor.getSelectionModel().getSelectionStart());
      int endLine = document.getLineNumber(editor.getSelectionModel().getSelectionEnd());
      for (int line = startLine; line <= endLine; line++) {
        int lineStart = document.getLineStartOffset(line);
        CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(project);
        int newPos = codeStyleManager.adjustLineIndent(file, lineStart);
        if (startLine == endLine && editor.getCaretModel().getOffset() < newPos) {
          editor.getCaretModel().moveToOffset(newPos);
          editor.getSelectionModel().removeSelection();
          editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
        }
      }
    }
  }
}
