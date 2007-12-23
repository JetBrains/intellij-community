package com.intellij.codeInsight.template;

import com.intellij.psi.PsiElement;

public interface Result {
  boolean equalsToText (String text, PsiElement context);

  String toString();
}

