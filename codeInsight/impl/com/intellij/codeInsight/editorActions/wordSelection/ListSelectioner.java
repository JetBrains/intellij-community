package com.intellij.codeInsight.editorActions.wordSelection;

import com.intellij.psi.*;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.editor.Editor;

import java.util.List;
import java.util.ArrayList;

public class ListSelectioner extends BasicSelectioner {
  public boolean canSelect(PsiElement e) {
    return e instanceof PsiParameterList || e instanceof PsiExpressionList;
  }

  public List<TextRange> select(PsiElement e, CharSequence editorText, int cursorOffset, Editor editor) {

    PsiElement[] children = e.getChildren();

    int start = 0;
    int end = 0;

    for (PsiElement child : children) {
      if (child instanceof PsiJavaToken) {
        PsiJavaToken token = (PsiJavaToken)child;

        if (token.getTokenType() == JavaTokenType.LPARENTH) {
          start = token.getTextOffset() + 1;
        }
        if (token.getTokenType() == JavaTokenType.RPARENTH) {
          end = token.getTextOffset();
        }
      }
    }

    List<TextRange> result = new ArrayList<TextRange>();
    result.add(new TextRange(start, end));
    return result;
  }
}
