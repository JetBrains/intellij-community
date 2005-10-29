/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.psi.search.searches;

import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.util.Query;
import com.intellij.util.QueryFactory;

/**
 * @author max
 */
public class OverridingMethodsSearch extends QueryFactory<PsiMethod, OverridingMethodsSearch.SearchParameters> {
  public static OverridingMethodsSearch INSTANCE = new OverridingMethodsSearch();

  public static class SearchParameters {
    private final PsiMethod myMethod;
    private final SearchScope myScope;
    private final boolean myCheckDeep;

    public SearchParameters(final PsiMethod aClass, SearchScope scope, final boolean checkDeep) {
      myMethod = aClass;
      myScope = scope;
      myCheckDeep = checkDeep;
    }

    public PsiMethod getMethod() {
      return myMethod;
    }

    public boolean isCheckDeep() {
      return myCheckDeep;
    }

    public SearchScope getScope() {
      return myScope;
    }
  }

  private OverridingMethodsSearch() {
  }

  public Query<PsiMethod, SearchParameters> createSearch(final PsiMethod method, SearchScope scope, final boolean checkDeep) {
    return createQuery(new SearchParameters(method, scope, checkDeep));
  }

  public Query<PsiMethod, SearchParameters> createSearch(final PsiMethod method, final boolean checkDeep) {
    return createQuery(new SearchParameters(method, GlobalSearchScope.allScope(method.getProject()), checkDeep));
  }

  public Query<PsiMethod, SearchParameters> createSearch(final PsiMethod method) {
    return createSearch(method, true);
  }
}
