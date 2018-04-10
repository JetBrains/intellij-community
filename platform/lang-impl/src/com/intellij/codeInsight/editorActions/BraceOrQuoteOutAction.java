// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.editorActions;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.highlighting.BraceMatcher;
import com.intellij.codeInsight.highlighting.BraceMatchingUtil;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtilBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class BraceOrQuoteOutAction extends EditorAction {
  public BraceOrQuoteOutAction() {
    super(new Handler());
  }

  private static class Handler extends EditorActionHandler {
    private Handler() {
      super(true);
    }

    @Override
    protected boolean isEnabledForCaret(@NotNull Editor editor, @NotNull Caret caret, DataContext dataContext) {
      return getCaretShift(editor, caret) != 0;
    }

    @Override
    protected void doExecute(@NotNull Editor editor, @Nullable Caret caret, DataContext dataContext) {
      assert caret != null;
      int caretShift = getCaretShift(editor, caret);
      if (caretShift != 0) {
        caret.moveToOffset(caret.getOffset() + caretShift);
      }
    }

    private static int getCaretShift(@NotNull Editor editor, @NotNull Caret caret) {
      if (!CodeInsightSettings.getInstance().TAB_EXITS_BRACKETS_AND_QUOTES || !(editor instanceof EditorEx)) return 0;

      Project project = editor.getProject();
      if (project == null) return 0;
      PsiFile psiFile = PsiUtilBase.getPsiFileInEditor(editor, project);
      if (psiFile == null) return 0;
      FileType fileType = TypedHandler.getFileType(psiFile, editor);

      int caretOffset = caret.getOffset();
      HighlighterIterator iterator = ((EditorEx)editor).getHighlighter().createIterator(caretOffset);

      BraceMatcher braceMatcher = BraceMatchingUtil.getBraceMatcher(fileType, iterator);
      if (caretOffset == iterator.getStart() &&
          braceMatcher.isRBraceToken(iterator, editor.getDocument().getImmutableCharSequence(), fileType)) {
        return iterator.getEnd() - caretOffset;
      }
      else {
        QuoteHandler quoteHandler = TypedHandler.getQuoteHandler(psiFile, editor);
        if (quoteHandler != null && quoteHandler.isClosingQuote(iterator, caretOffset)) {
          return  1;
        }
      }
      return 0;
    }
  }
}
