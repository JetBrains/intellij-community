package com.intellij.codeInsight.template;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.openapi.editor.Document;

public interface Result {
  boolean equalsToText (String text, PsiElement context);

  String toString();

  void handleFocused(final PsiFile psiFile, final Document document, final int segmentStart, final int segmentEnd);
}

