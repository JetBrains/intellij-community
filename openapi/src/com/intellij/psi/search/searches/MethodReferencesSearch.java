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

    public SearchParameters(final PsiMethod aClass, SearchScope scope, final boolean strict) {
      myMethod = aClass;
      myScope = scope;
      myStrictSignatureSearch = strict;
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

  private MethodReferencesSearch() {}

  public static Query<PsiReference> search(final PsiMethod method, SearchScope scope, final boolean strictSignatureSearch) {
    return INSTANCE.createUniqueResultsQuery(new MethodReferencesSearch.SearchParameters(method, scope, strictSignatureSearch));
  }

  public static Query<PsiReference> search(final PsiMethod method, final boolean strictSignatureSearch) {
    return search(method, GlobalSearchScope.allScope(method.getProject()), strictSignatureSearch);
  }

  public static Query<PsiReference> search(final PsiMethod method) {
    return search(method, true);
  }
}