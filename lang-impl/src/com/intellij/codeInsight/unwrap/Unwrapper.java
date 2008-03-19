package com.intellij.codeInsight.unwrap;

import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;

import java.util.List;
import java.util.Set;

public interface Unwrapper {
  boolean isApplicableTo(PsiElement e);

  void collectElementsToIgnore(PsiElement element, Set<PsiElement> result);

  String getDescription(PsiElement e);

  /**
   * @param toExtract the elements that will be extracted
   * @return TextRange the whole affected code structure (the code that will be removed)
   */
  PsiElement collectAffectedElements(PsiElement e, List<PsiElement> toExtract);

  void unwrap(Editor editor, PsiElement element) throws IncorrectOperationException;
}
