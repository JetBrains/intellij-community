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
package com.intellij.codeInsight.editorActions;

import com.intellij.openapi.editor.Document;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaToken;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;

public class LiteralJoinLinesHandler implements JoinLinesHandlerDelegate {

  private static final int STATE_INITIAL = 0;
  private static final int STATE_BEFORE_PLUS = 1;
  private static final int STATE_AFTER_PLUS = 2;

  @Override
  public int tryJoinLines(@NotNull final Document doc, @NotNull final PsiFile psiFile, final int offsetNear, final int end) {
    CharSequence text = doc.getCharsSequence();

    int start = offsetNear;
    while (text.charAt(start) == ' ' || text.charAt(start) == '\t' || text.charAt(start) == '+') start--;
    if (text.charAt(start) == '\"') start--;
    if (start < offsetNear) start++;

    int state = STATE_INITIAL;
    int startQuoteOffset = -1;
    PsiElement parentExpression = null;
    for (int j = start; j < doc.getTextLength(); j++) {
      switch (text.charAt(j)) {
        case ' ':
        case '\t':
          break;

        case '\"':
          PsiJavaToken token = ObjectUtils.tryCast(psiFile.findElementAt(j), PsiJavaToken.class);
          if (token == null || token.getTokenType() != JavaTokenType.STRING_LITERAL) return -1;
          if (state == STATE_INITIAL) {
            state = STATE_BEFORE_PLUS;
            startQuoteOffset = j;
            // token.getParent() = PsiLiteralExpression
            parentExpression = token.getParent().getParent();
            break;
          }

          if (state == STATE_AFTER_PLUS) {
            if (token.getParent().getParent() != parentExpression) return -1;
            doc.deleteString(startQuoteOffset, j + 1);
            return startQuoteOffset;
          }
          return -1;

        case '+':
          if (state != STATE_BEFORE_PLUS) return -1;
          state = STATE_AFTER_PLUS;
          break;

        default:
          return -1;
      }
    }

    return -1;
  }
}
