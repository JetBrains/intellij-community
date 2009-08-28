
package com.intellij.codeInsight.template;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.openapi.editor.Document;

public class InvokeActionResult implements Result{
  private final Runnable myAction;

  public InvokeActionResult(Runnable action) {
    myAction = action;
  }

  public Runnable getAction() {
    return myAction;
  }

  public boolean equalsToText(String text, PsiElement context) {
    return true; //no text result will be provided anyway
  }

  public String toString() {
    return "";
  }

  public void handleFocused(final PsiFile psiFile, final Document document, final int segmentStart, final int segmentEnd) {
    myAction.run();
  }
}