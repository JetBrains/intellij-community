/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.codeInsight.editorActions.enter;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.highlighting.BraceMatcher;
import com.intellij.codeInsight.highlighting.BraceMatchingUtil;
import com.intellij.lang.Language;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.text.CharArrayUtil;

public class EnterAfterUnmatchedBraceHandler implements EnterHandlerDelegate {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.editorActions.enter.EnterAfterUnmatchedBraceHandler");

  public Result preprocessEnter(final PsiFile file, final Editor editor, final Ref<Integer> caretOffsetRef, final Ref<Integer> caretAdvance,
                                final DataContext dataContext, final EditorActionHandler originalHandler) {
    Document document = editor.getDocument();
    CharSequence text = document.getText();
    Project project = file.getProject();
    int caretOffset = caretOffsetRef.get().intValue();
    if (CodeInsightSettings.getInstance().INSERT_BRACE_ON_ENTER && isAfterUnmatchedLBrace(editor, caretOffset, file.getFileType())) {
      int offset = CharArrayUtil.shiftForward(text, caretOffset, " \t");
      if (offset < document.getTextLength()) {
        char c = text.charAt(offset);
        if (c != ')' && c != ']' && c != ';' && c != ',' && c != '%' && c != '<' && c != '?') {
          offset = CharArrayUtil.shiftForwardUntil(text, caretOffset, "\n");
        }
      }
      offset = Math.min(offset, document.getTextLength());

      document.insertString(offset, "\n}");
      PsiDocumentManager.getInstance(project).commitDocument(document);
      try {
        CodeStyleManager.getInstance(project).adjustLineIndent(file, offset + 1);
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
      return Result.DefaultForceIndent;
    }
    return Result.Continue;
  }

  public static boolean isAfterUnmatchedLBrace(Editor editor, int offset, FileType fileType) {
    if (offset == 0) return false;
    CharSequence chars = editor.getDocument().getCharsSequence();
    if (chars.charAt(offset - 1) != '{') return false;

    EditorHighlighter highlighter = ((EditorEx)editor).getHighlighter();
    HighlighterIterator iterator = highlighter.createIterator(offset - 1);
    BraceMatcher braceMatcher = BraceMatchingUtil.getBraceMatcher(fileType, iterator);

    if (!braceMatcher.isLBraceToken(iterator, chars, fileType) ||
        !braceMatcher.isStructuralBrace(iterator, chars, fileType)
        ) {
      return false;
    }

    Language language = iterator.getTokenType().getLanguage();

    iterator = highlighter.createIterator(0);
    int balance = 0;
    while (!iterator.atEnd()) {
      IElementType tokenType = iterator.getTokenType();
      if (tokenType.getLanguage().equals(language)) {
        if (braceMatcher.isStructuralBrace(iterator, chars, fileType)) {
          if (braceMatcher.isLBraceToken(iterator, chars, fileType)) {
            balance++;
          } else if (braceMatcher.isRBraceToken(iterator, chars, fileType)) {
            balance--;
          }
        }
      }
      iterator.advance();
    }
    return balance > 0;
  }
}
