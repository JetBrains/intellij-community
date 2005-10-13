package com.intellij.codeInsight.template;

import com.intellij.psi.PsiElement;

public class TextResult implements Result{
  private final String myText;

  public TextResult(String text) {
    myText = text;
  }

  public String getText() {
    return myText;
  }

  public boolean equalsToText(String text, PsiElement context) {
    return text.equals(myText);
  }

  public String toString() {
    return myText;
  }
}