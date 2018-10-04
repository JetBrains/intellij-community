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

import com.intellij.codeInsight.editorActions.EnterHandler;
import com.intellij.lang.CodeDocumentationAwareCommenter;
import com.intellij.lang.Commenter;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageCommenters;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiFile;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;

public class EnterInLineCommentHandler extends EnterHandlerDelegateAdapter {
  private static final String WHITESPACE = " \t";

  @Override
  public Result preprocessEnter(@NotNull final PsiFile file,
                                @NotNull final Editor editor,
                                @NotNull final Ref<Integer> caretOffsetRef,
                                @NotNull final Ref<Integer> caretAdvance,
                                @NotNull final DataContext dataContext,
                                final EditorActionHandler originalHandler) {
    final Language language = EnterHandler.getLanguage(dataContext);
    if (language == null) return Result.Continue;
    final Commenter languageCommenter = LanguageCommenters.INSTANCE.forLanguage(language);
    final CodeDocumentationAwareCommenter commenter = languageCommenter instanceof CodeDocumentationAwareCommenter
                                                      ? (CodeDocumentationAwareCommenter)languageCommenter : null;
    if (commenter == null) return Result.Continue;
    int caretOffset = caretOffsetRef.get().intValue();
    int lineCommentStartOffset = getLineCommentStartOffset(editor, caretOffset, commenter);
    if (lineCommentStartOffset >= 0) {
        Document document = editor.getDocument();
        CharSequence text = document.getText();
        final int offset = CharArrayUtil.shiftForward(text, caretOffset, WHITESPACE);
        if (offset < document.getTextLength() && text.charAt(offset) != '\n') {
          String prefix = commenter.getLineCommentPrefix();
          assert prefix != null : "Line Comment type is set but Line Comment Prefix is null!";
          if (!StringUtil.startsWith(text, offset, prefix)) {
            if (text.charAt(caretOffset) != ' ' && !prefix.endsWith(" ")) {
              prefix += " ";
            }
            document.insertString(caretOffset, prefix);
          }
          else {
            int afterPrefix = offset + prefix.length();
            if (afterPrefix < document.getTextLength() && text.charAt(afterPrefix) != ' ') {
              document.insertString(afterPrefix, " ");
            }
            caretOffsetRef.set(offset);
          }

          int beforeCommentOffset = CharArrayUtil.shiftBackward(text, lineCommentStartOffset - 1, WHITESPACE);
          if (beforeCommentOffset < 0 || text.charAt(beforeCommentOffset) == '\n') {
            caretAdvance.set(prefix.trim().length() + 1);
          }
          return Result.DefaultForceIndent;
        }
    }
    return Result.Continue;
  }
  
  private static int getLineCommentStartOffset(@NotNull Editor editor, int offset, @NotNull CodeDocumentationAwareCommenter commenter) {
    if (offset < 1) return -1;
    EditorHighlighter highlighter = ((EditorEx)editor).getHighlighter();
    HighlighterIterator iterator = highlighter.createIterator(offset - 1);
    String prefix = commenter.getLineCommentPrefix();
    return iterator.getTokenType() == commenter.getLineCommentTokenType() &&
           (iterator.getStart() + (prefix == null ? 0 : prefix.length())) <= offset ? iterator.getStart() : -1;
  }
}
