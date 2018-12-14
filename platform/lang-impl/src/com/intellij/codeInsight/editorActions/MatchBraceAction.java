// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.editorActions;

import com.intellij.codeInsight.highlighting.BraceHighlightingHandler;
import com.intellij.codeInsight.highlighting.BraceMatcher;
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
 * Moves caret to the the matching brace:
 * - If caret is on the closing brace - moves to the matching opening
 * - If caret is on the opening brace - moves to the matching closing brace
 * - Otherwise algorithm is clumsy from user perspective: look for the previous opening or close brace and move to the matching one
 */
public class MatchBraceAction extends EditorAction {
  public MatchBraceAction() {
    super(new MyHandler());
  }

  private static class MyHandler extends EditorActionHandler {
    MyHandler() {
      super(true);
    }

    @Override
    public void execute(@NotNull Editor editor, DataContext dataContext) {
      final PsiFile file = CommonDataKeys.PSI_FILE.getData(dataContext);
      if (file == null) return;

      int offsetFromBraceMatcher = getOffsetFromBraceMatcher(editor, file);
      if (offsetFromBraceMatcher > -1) {
        moveCaret(editor, editor.getCaretModel().getCurrentCaret(), offsetFromBraceMatcher);
      }
    }

    /**
     * @return offset to move caret to, computed from the brace matcher. If it's not possible to compute - returns {@code -1}
     * @implNote this code partially duplicates {@link BraceHighlightingHandler#updateBraces()} and probably can be extracted.
     */
    private static int getOffsetFromBraceMatcher(@NotNull Editor editor, @NotNull PsiFile file) {
      final Caret caret = editor.getCaretModel().getCurrentCaret();
      final EditorHighlighter highlighter = BraceHighlightingHandler.getLazyParsableHighlighterIfAny(file.getProject(), editor, file);
      final CharSequence text = editor.getDocument().getCharsSequence();

      int offset = caret.getOffset();
      FileType fileType = getFileType(file, offset);
      HighlighterIterator iterator = highlighter.createIterator(offset);

      if (iterator.atEnd()) {
        // end of file, caret should be in the beginning of the brace
        offset--;
      }
      else if (offset > 0 &&
               !BraceMatchingUtil.isLBraceToken(iterator, text, fileType) &&
               !BraceMatchingUtil.isRBraceToken(iterator, text, fileType)) {
        // we probably standing after a brace, let's look behind one char
        offset--;

        HighlighterIterator i = highlighter.createIterator(offset);
        if (!BraceMatchingUtil.isRBraceToken(i, text, getFileType(file, i.getStart())) &&
            !BraceMatchingUtil.isLBraceToken(i, text, getFileType(file, i.getStart()))) {
          // we still not at brace
          offset++;
        }
      }

      if (offset < 0) return -1;

      iterator = highlighter.createIterator(offset);
      fileType = getFileType(file, iterator.getStart());

      boolean isLeftBrace = BraceMatchingUtil.isLBraceToken(iterator, text, fileType);
      if (isLeftBrace || BraceMatchingUtil.isRBraceToken(iterator, text, fileType)) {
        // we are at brace
        if (BraceMatchingUtil.matchBrace(text, fileType, iterator, isLeftBrace)) {
          return iterator.getStart();
        }
        return -1;
      }

      int unopenedBraces = 0;
      while (true) {
        if (BraceMatchingUtil.isRBraceToken(iterator, text, fileType)) {
          unopenedBraces++;
        }
        else if (BraceMatchingUtil.isLBraceToken(iterator, text, fileType)) {
          unopenedBraces--;
        }
        if (unopenedBraces < 0) {
          return iterator.getStart();
        }

        if (iterator.getStart() == 0) {
          return -1;
        }
        iterator.retreat();
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
