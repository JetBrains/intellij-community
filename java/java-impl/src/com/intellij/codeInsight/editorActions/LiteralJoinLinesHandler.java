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
import org.jetbrains.annotations.NotNull;

public class LiteralJoinLinesHandler implements JoinLinesHandlerDelegate {
  @Override
  public int tryJoinLines(@NotNull final Document doc, @NotNull final PsiFile psiFile, final int offsetNear, final int end) {
    CharSequence text = doc.getCharsSequence();

    int start = offsetNear;
    while (text.charAt(start) == ' ' || text.charAt(start) == '\t' || text.charAt(start) == '+') start--;
    if (text.charAt(start) == '\"') start--;
    if (start < offsetNear) start++;

    int state = 0;
    int startQuoteOffset = -1;
    state_loop:
    for (int j = start; j < doc.getTextLength(); j++) {
      switch (text.charAt(j)) {
        case ' ':
        case '\t':
          break;

        case '\"':
          if (state == 0) {
            state = 1;
            startQuoteOffset = j;
            PsiElement psiAtOffset = psiFile.findElementAt(j);
            if (!(psiAtOffset instanceof PsiJavaToken)) return -1;
            if (((PsiJavaToken)psiAtOffset).getTokenType() != JavaTokenType.STRING_LITERAL) return -1;
            break;
          }

          if (state == 2) {
            doc.deleteString(startQuoteOffset, j + 1);
            return startQuoteOffset;
          }
          break state_loop;

        case '+':
          if (state != 1) break state_loop;
          state = 2;
          break;

        default:
          break state_loop;
      }
    }

    return -1;
  }
}
