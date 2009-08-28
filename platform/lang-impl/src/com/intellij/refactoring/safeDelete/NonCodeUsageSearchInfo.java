package com.intellij.refactoring.safeDelete;

import com.intellij.openapi.util.Condition;
import com.intellij.psi.PsiElement;

import java.util.Collection;
import java.util.Collections;

/**
 * @author yole
 */
public class NonCodeUsageSearchInfo {
  private final Condition<PsiElement> myInsideDeletedCondition;
  private final Collection<PsiElement> myElementsToSearch;

  public NonCodeUsageSearchInfo(final Condition<PsiElement> insideDeletedCondition, final Collection<PsiElement> elementsToSearch) {
    myInsideDeletedCondition = insideDeletedCondition;
    myElementsToSearch = elementsToSearch;
  }

  public NonCodeUsageSearchInfo(final Condition<PsiElement> insideDeletedCondition, final PsiElement elementToSearch) {
    myInsideDeletedCondition = insideDeletedCondition;
    myElementsToSearch = Collections.singletonList(elementToSearch);
  }

  public Condition<PsiElement> getInsideDeletedCondition() {
    return myInsideDeletedCondition;
  }

  public Collection<PsiElement> getElementsToSearch() {
    return myElementsToSearch;
  }
}
