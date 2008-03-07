package com.intellij.codeInsight.unwrap;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;

import java.util.List;
import java.util.Set;

public interface Unwrapper {
  boolean isApplicableTo(PsiElement e);

  void collectElementsToIgnore(PsiElement element, Set<PsiElement> result);

  String getDescription(PsiElement e);

  /**
   * @param toExtract text ranges that will be extracted
   * @return TextRange that represents the whole affected code structure (the code that will be removed)
   */
  TextRange collectTextRanges(PsiElement e, List<TextRange> toExtract);

  void unwrap(Editor editor, PsiElement element) throws IncorrectOperationException;
}
