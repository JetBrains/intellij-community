// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.refactoring.safeDelete;

import com.intellij.openapi.util.Condition;
import com.intellij.psi.PsiElement;

import java.util.Collection;
import java.util.Collections;


public class NonCodeUsageSearchInfo {
  private final Condition<? super PsiElement> myInsideDeletedCondition;
  private final Collection<? extends PsiElement> myElementsToSearch;

  public NonCodeUsageSearchInfo(final Condition<? super PsiElement> insideDeletedCondition, final Collection<? extends PsiElement> elementsToSearch) {
    myInsideDeletedCondition = insideDeletedCondition;
    myElementsToSearch = elementsToSearch;
  }

  public NonCodeUsageSearchInfo(final Condition<? super PsiElement> insideDeletedCondition, final PsiElement elementToSearch) {
    myInsideDeletedCondition = insideDeletedCondition;
    myElementsToSearch = Collections.singletonList(elementToSearch);
  }

  public Condition<? super PsiElement> getInsideDeletedCondition() {
    return myInsideDeletedCondition;
  }

  public Collection<? extends PsiElement> getElementsToSearch() {
    return myElementsToSearch;
  }
}
