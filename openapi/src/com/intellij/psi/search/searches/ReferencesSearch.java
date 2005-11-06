/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.psi.search.searches;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.SearchScope;
import com.intellij.util.Query;
import com.intellij.util.QueryFactory;

/**
 * @author max
 */
public class ReferencesSearch extends QueryFactory<PsiReference, ReferencesSearch.SearchParameters> {
  public static ReferencesSearch INSTANCE = new ReferencesSearch();

  private ReferencesSearch() {
  }

  public static class SearchParameters {
    private final PsiElement myElementToSearch;
    private final SearchScope myScope;
    private final boolean myIgnoreAcccessScope;

    public SearchParameters(final PsiElement elementToSearch, final SearchScope scope, final boolean ignoreAcccessScope) {
      myElementToSearch = elementToSearch;
      myScope = scope;
      myIgnoreAcccessScope = ignoreAcccessScope;
    }

    public PsiElement getElementToSearch() {
      return myElementToSearch;
    }

    public SearchScope getScope() {
      return myScope;
    }

    public boolean isIgnoreAcccessScope() {
      return myIgnoreAcccessScope;
    }
  }

  public static Query<PsiReference> search(PsiElement element) {
    return search(element, element.getUseScope(), true);
  }

  public static Query<PsiReference> search(PsiElement element, SearchScope searchScope) {
    return search(element, searchScope, false);
  }

  public static Query<PsiReference> search(PsiElement element, SearchScope searchScope, boolean ignoreAccessScope) {
    return INSTANCE.createQuery(new SearchParameters(element, searchScope, ignoreAccessScope));
  }
}
