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
package com.intellij.codeInsight.editorActions.wordSelection;

import com.intellij.codeInsight.editorActions.SelectWordUtil;
import com.intellij.lexer.StringLiteralLexer;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.StringEscapesTokenTypes;

import java.util.List;

public class LiteralSelectioner extends BasicSelectioner {
  @Override
  public boolean canSelect(PsiElement e) {
    PsiElement parent = e.getParent();
    return
      isStringLiteral(e) || isStringLiteral(parent);
  }

  private static boolean isStringLiteral(PsiElement element) {
    return element instanceof PsiLiteralExpression &&
           ((PsiLiteralExpression)element).getType().equalsToText("java.lang.String") && element.getText().startsWith("\"") && element.getText().endsWith("\"");
  }

  @Override
  public List<TextRange> select(PsiElement e, CharSequence editorText, int cursorOffset, Editor editor) {
    List<TextRange> result = super.select(e, editorText, cursorOffset, editor);

    TextRange range = e.getTextRange();
    final StringLiteralLexer lexer = new StringLiteralLexer('\"', JavaTokenType.STRING_LITERAL);
    lexer.start(editorText, range.getStartOffset(), range.getEndOffset());

    while (lexer.getTokenType() != null) {
      if (lexer.getTokenStart() <= cursorOffset && cursorOffset < lexer.getTokenEnd()) {
        if (StringEscapesTokenTypes.STRING_LITERAL_ESCAPES.contains(lexer.getTokenType())) {
          result.add(new TextRange(lexer.getTokenStart(), lexer.getTokenEnd()));
        }
        else {
          TextRange word = SelectWordUtil.getWordSelectionRange(editorText, cursorOffset);
          if (word != null) {
            result.add(new TextRange(Math.max(word.getStartOffset(), lexer.getTokenStart()),
                                     Math.min(word.getEndOffset(), lexer.getTokenEnd())));
          }
        }
        break;
      }
      lexer.advance();
    }

    result.add(new TextRange(range.getStartOffset() + 1, range.getEndOffset() - 1));

    return result;
  }
}
