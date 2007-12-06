package com.intellij.codeInsight.editorActions;

import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaToken;
import com.intellij.psi.JavaTokenType;

public class LiteralJoinLinesHandler implements JoinLinesHandlerDelegate {
  public int tryJoinLines(final DocumentEx doc, final PsiFile psiFile, final int offsetNear, final int end) {
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
