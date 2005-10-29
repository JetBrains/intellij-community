/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.psi.search.searches;

import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.util.Query;
import com.intellij.util.QueryFactory;

/**
 * @author max
 */
public class MethodReferencesSearch extends QueryFactory<PsiReference, MethodReferencesSearch.SearchParameters> {
  public static MethodReferencesSearch INSTANCE = new MethodReferencesSearch();

  public static class SearchParameters {
    private final PsiMethod myMethod;
    private final SearchScope myScope;
    private final boolean myStrictSignatureSearch;

    public SearchParameters(final PsiMethod aClass, SearchScope scope, final boolean checkDeep) {
      myMethod = aClass;
      myScope = scope;
      myStrictSignatureSearch = checkDeep;
    }

    public PsiMethod getMethod() {
      return myMethod;
    }

    public boolean isStrictSignatureSearch() {
      return myStrictSignatureSearch;
    }

    public SearchScope getScope() {
      return myScope;
    }
  }

  private MethodReferencesSearch() {
  }

  public Query<PsiReference, SearchParameters> createSearch(final PsiMethod method, SearchScope scope, final boolean strictSignatureSearch) {
    return createQuery(new MethodReferencesSearch.SearchParameters(method, scope, strictSignatureSearch));
  }

  public Query<PsiReference, MethodReferencesSearch.SearchParameters> createSearch(final PsiMethod method, final boolean strictSignatureSearch) {
    return createQuery(new MethodReferencesSearch.SearchParameters(method, GlobalSearchScope.allScope(method.getProject()), strictSignatureSearch));
  }

  public Query<PsiReference, MethodReferencesSearch.SearchParameters> createSearch(final PsiMethod method) {
    return createSearch(method, true);
  }
}