// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.editorActions;

import com.intellij.codeInsight.highlighting.BraceMatchingUtil;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorModificationUtil;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtilBase;
import org.jetbrains.annotations.NotNull;

/**
 * @author Denis Zhdanov
 * @since 10/24/12 11:10 AM
 */
public class MatchBraceAction extends EditorAction {
  public MatchBraceAction() {
    super(new MyHandler());
  }

  private static class MyHandler extends EditorActionHandler {
    public MyHandler() {
      super(true);
    }

    @Override
    public void execute(@NotNull Editor editor, DataContext dataContext) {
      final PsiFile file = CommonDataKeys.PSI_FILE.getData(dataContext);
      if (file == null) return;

      final Caret caret = editor.getCaretModel().getCurrentCaret();
      final EditorHighlighter highlighter = ((EditorEx)editor).getHighlighter();
      final CharSequence text = editor.getDocument().getCharsSequence();

      int offset = caret.getOffset();
      FileType fileType = getFileType(file, offset);
      HighlighterIterator iterator = highlighter.createIterator(offset);

      if (iterator.atEnd()) {
        offset--;
      }
      else if (!BraceMatchingUtil.isLBraceToken(iterator, text, fileType)) {
        offset--;

        if (offset >= 0) {
          final HighlighterIterator i = highlighter.createIterator(offset);
          if (!BraceMatchingUtil.isRBraceToken(i, text, getFileType(file, i.getStart()))) offset++;
        }
      }

      if (offset < 0) return;

      iterator = highlighter.createIterator(offset);
      fileType = getFileType(file, iterator.getStart());

      while (!BraceMatchingUtil.isLBraceToken(iterator, text, fileType) &&
             !BraceMatchingUtil.isRBraceToken(iterator, text, fileType)) {
        if (iterator.getStart() == 0) return;
        iterator.retreat();
        offset = iterator.getStart();
      }

      if (BraceMatchingUtil.matchBrace(text, fileType, iterator, true)) {
        moveCaret(editor, caret, iterator.getEnd());
        return;
      }
      iterator = highlighter.createIterator(offset);
      if (BraceMatchingUtil.matchBrace(text, fileType, iterator, false)) {
        moveCaret(editor, caret, iterator.getStart());
      }
    }
  }

  @NotNull
  private static FileType getFileType(PsiFile file, int offset) {
    return PsiUtilBase.getPsiFileAtOffset(file, offset).getFileType();
  }

  private static void moveCaret(Editor editor, Caret caret, int offset) {
    caret.removeSelection();
    caret.moveToOffset(offset);
    EditorModificationUtil.scrollToCaret(editor);
  }
}
