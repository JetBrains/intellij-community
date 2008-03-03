package com.intellij.codeInsight.unwrap;

import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;

public interface Unwrapper {
  boolean isApplicableTo(PsiElement e);

  String getDescription(PsiElement e);

  void unwrap(Editor editor, PsiElement element) throws IncorrectOperationException;
}
