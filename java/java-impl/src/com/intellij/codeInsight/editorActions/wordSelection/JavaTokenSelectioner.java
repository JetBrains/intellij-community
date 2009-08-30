package com.intellij.codeInsight.editorActions.wordSelection;

import com.intellij.psi.*;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.editor.Editor;

import java.util.List;

public class JavaTokenSelectioner extends BasicSelectioner {
  public boolean canSelect(PsiElement e) {
    return e instanceof PsiJavaToken && !(e instanceof PsiKeyword) && !(e.getParent()instanceof PsiCodeBlock);
  }

  public List<TextRange> select(PsiElement e, CharSequence editorText, int cursorOffset, Editor editor) {
    PsiJavaToken token = (PsiJavaToken)e;

    if (token.getTokenType() != JavaTokenType.SEMICOLON && token.getTokenType() != JavaTokenType.LPARENTH) {
      return super.select(e, editorText, cursorOffset, editor);
    }
    else {
      return null;
    }
  }
}
