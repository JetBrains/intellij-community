// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.refactoring.safeDelete;

import com.intellij.openapi.util.Condition;
import com.intellij.psi.PsiElement;

import java.util.Collection;
import java.util.Collections;


public final class NonCodeUsageSearchInfo {
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
