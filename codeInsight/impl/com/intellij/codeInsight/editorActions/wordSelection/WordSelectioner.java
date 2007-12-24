package com.intellij.codeInsight.editorActions.wordSelection;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaToken;
import com.intellij.psi.JavaTokenType;

public class WordSelectioner extends AbstractWordSelectioner {
  public boolean canSelect(PsiElement e) {
    return BasicSelectioner.canSelectBasic(e) ||
           e instanceof PsiJavaToken && ((PsiJavaToken)e).getTokenType() == JavaTokenType.IDENTIFIER;
  }

}
