package com.intellij.codeInsight.template;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.openapi.editor.Document;

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

  public void handleFocused(final PsiFile psiFile, final Document document, final int segmentStart, final int segmentEnd) {
  }
}