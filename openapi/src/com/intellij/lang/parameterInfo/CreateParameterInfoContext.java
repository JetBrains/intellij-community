package com.intellij.lang.parameterInfo;

import com.intellij.psi.PsiElement;

public interface CreateParameterInfoContext extends ParameterInfoContext {
  Object[] getItemsToShow();
  void setItemsToShow(Object[] items);

  void showHint(PsiElement element, int offset, ParameterInfoHandler handler);
  int getParameterListStart();

  PsiElement getHighlightedElement();
  void setHighlightedElement(PsiElement elements);
}
