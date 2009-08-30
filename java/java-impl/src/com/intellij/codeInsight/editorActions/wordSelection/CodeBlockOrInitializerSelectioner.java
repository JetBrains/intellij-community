package com.intellij.codeInsight.editorActions.wordSelection;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;

import java.util.ArrayList;
import java.util.List;

public class CodeBlockOrInitializerSelectioner extends BasicSelectioner {
  public boolean canSelect(PsiElement e) {
    return e instanceof PsiCodeBlock || e instanceof PsiArrayInitializerExpression;
  }

  public List<TextRange> select(PsiElement e, CharSequence editorText, int cursorOffset, Editor editor) {
    List<TextRange> result = new ArrayList<TextRange>();

    PsiElement[] children = e.getChildren();

    int start = findOpeningBrace(children);
    int end = findClosingBrace(children, start);

    result.add(e.getTextRange());
    result.addAll(expandToWholeLine(editorText, new TextRange(start, end)));

    return result;
  }

  public static int findOpeningBrace(PsiElement[] children) {
    int start = 0;
    for (int i = 0; i < children.length; i++) {
      PsiElement child = children[i];

      if (child instanceof PsiJavaToken) {
        PsiJavaToken token = (PsiJavaToken)child;

        if (token.getTokenType() == JavaTokenType.LBRACE) {
          int j = i + 1;

          while (children[j] instanceof PsiWhiteSpace) {
            j++;
          }

          start = children[j].getTextRange().getStartOffset();
        }
      }
    }
    return start;
  }

  public static int findClosingBrace(PsiElement[] children, int startOffset) {
    int end = children[children.length - 1].getTextRange().getEndOffset();
    for (int i = 0; i < children.length; i++) {
      PsiElement child = children[i];

      if (child instanceof PsiJavaToken) {
        PsiJavaToken token = (PsiJavaToken)child;

        if (token.getTokenType() == JavaTokenType.RBRACE) {
          int j = i - 1;

          while (children[j] instanceof PsiWhiteSpace && children[j].getTextRange().getStartOffset() > startOffset) {
            j--;
          }

          end = children[j].getTextRange().getEndOffset();
        }
      }
    }
    return end;
  }
}
