/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.psi.search.searches;

import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.util.Query;
import com.intellij.util.QueryFactory;

/**
 * @author max
 */
public class DirectClassInheritorsSearch extends QueryFactory<PsiClass, DirectClassInheritorsSearch.SearchParameters> {
  public static DirectClassInheritorsSearch INSTANCE = new DirectClassInheritorsSearch();

  public static class SearchParameters {
    private final PsiClass myClass;
    private final SearchScope myScope;

    public SearchParameters(final PsiClass aClass, SearchScope scope) {
      myClass = aClass;
      myScope = scope;
    }

    public PsiClass getClassToProcess() {
      return myClass;
    }

    public SearchScope getScope() {
      return myScope;
    }
  }

  private DirectClassInheritorsSearch() {}

  public static Query<PsiClass> search(final PsiClass aClass) {
    return search(aClass, GlobalSearchScope.allScope(aClass.getProject()));
  }

  public static Query<PsiClass> search(final PsiClass aClass, SearchScope scope) {
    return INSTANCE.createUniqueResultsQuery(new SearchParameters(aClass, scope));
  }
}
