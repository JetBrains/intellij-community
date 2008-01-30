package com.intellij.codeInsight.unwrap;

import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.editor.Editor;

public interface Unwrapper {
  boolean isApplicableTo(PsiElement e);

  String getDescription(PsiElement e);

  void unwrap(Project project, Editor editor, PsiElement element) throws IncorrectOperationException;
}
