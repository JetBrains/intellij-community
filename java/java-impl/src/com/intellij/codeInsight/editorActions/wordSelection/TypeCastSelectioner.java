package com.intellij.codeInsight.editorActions.wordSelection;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiTypeCastExpression;
import com.intellij.psi.PsiJavaToken;
import com.intellij.psi.JavaTokenType;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.editor.Editor;

import java.util.List;
import java.util.ArrayList;

public class TypeCastSelectioner extends BasicSelectioner {
  public boolean canSelect(PsiElement e) {
    return e instanceof PsiTypeCastExpression;
  }

  public List<TextRange> select(PsiElement e, CharSequence editorText, int cursorOffset, Editor editor) {
    List<TextRange> result = new ArrayList<TextRange>();
    result.addAll(expandToWholeLine(editorText, e.getTextRange(), false));

    PsiTypeCastExpression expression = (PsiTypeCastExpression)e;
    PsiElement[] children = expression.getChildren();
    PsiElement lParen = null;
    PsiElement rParen = null;
    for (PsiElement child : children) {
      if (child instanceof PsiJavaToken) {
        PsiJavaToken token = (PsiJavaToken)child;
        if (token.getTokenType() == JavaTokenType.LPARENTH) lParen = token;
        if (token.getTokenType() == JavaTokenType.RPARENTH) rParen = token;
      }
    }

    if (lParen != null && rParen != null) {
      result.addAll(expandToWholeLine(editorText,
                                      new TextRange(lParen.getTextRange().getStartOffset(),
                                                    rParen.getTextRange().getEndOffset()),
                                      false));
    }

    return result;
  }
}
