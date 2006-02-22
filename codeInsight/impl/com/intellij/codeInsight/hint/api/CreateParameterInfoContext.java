package com.intellij.codeInsight.hint.api;

import com.intellij.psi.PsiElement;

/**
 * Created by IntelliJ IDEA.
 * User: Maxim.Mossienko
 * Date: Jan 31, 2006
 * Time: 10:51:09 PM
 * To change this template use File | Settings | File Templates.
 */
public interface CreateParameterInfoContext extends ParameterInfoContext {
  Object[] getItemsToShow();
  void setItemsToShow(Object[] items);

  void showHint(PsiElement element, int offset, ParameterInfoHandler handler);
  int getParameterListStart();

  PsiElement getHighlightedElement();
  void setHighlightedElement(PsiElement elements);
}
