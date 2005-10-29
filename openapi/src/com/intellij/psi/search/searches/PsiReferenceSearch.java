/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.psi.search.searches;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.util.Query;
import com.intellij.util.QueryFactory;

/**
 * @author max
 */
public class PsiReferenceSearch extends QueryFactory<PsiReference, PsiReferenceSearch.SearchParameters> {
  public static PsiReferenceSearch INSTANCE = new PsiReferenceSearch();

  private PsiReferenceSearch() {
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

  public Query<PsiReference, SearchParameters> createSearch(PsiElement element) {
    return createSearch(element, GlobalSearchScope.allScope(element.getProject()));
  }

  public Query<PsiReference, SearchParameters> createSearch(PsiElement element, SearchScope searchScope) {
    return createSearch(element, searchScope, false);
  }

  public Query<PsiReference, SearchParameters> createSearch(PsiElement element, SearchScope searchScope, boolean ignoreAccessScope) {
    return createQuery(new SearchParameters(element, searchScope, ignoreAccessScope));
  }
}
