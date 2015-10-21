/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

    public void execute(Editor editor, DataContext dataContext) {
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
