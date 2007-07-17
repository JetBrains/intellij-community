/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.psi.search.searches;

import com.intellij.openapi.util.Condition;
import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.util.FilteredQuery;
import com.intellij.util.Query;

/**
 * @author max
 */
public class DirectClassInheritorsSearch extends ExtensibleQueryFactory<PsiClass, DirectClassInheritorsSearch.SearchParameters> {
  public static DirectClassInheritorsSearch INSTANCE = new DirectClassInheritorsSearch();

  public static class SearchParameters {
    private final PsiClass myClass;
    private final SearchScope myScope;
    private final boolean myIncludeAnonymous;

    public SearchParameters(final PsiClass aClass, SearchScope scope, final boolean includeAnonymous) {
      myIncludeAnonymous = includeAnonymous;
      myClass = aClass;
      myScope = scope;
    }

    public SearchParameters(final PsiClass aClass, final SearchScope scope) {
      myClass = aClass;
      myScope = scope;
      myIncludeAnonymous = true;
    }

    public PsiClass getClassToProcess() {
      return myClass;
    }

    public SearchScope getScope() {
      return myScope;
    }

    public boolean includeAnonymous() {
      return myIncludeAnonymous;
    }
  }

  private DirectClassInheritorsSearch() {}

  public static Query<PsiClass> search(final PsiClass aClass) {
    return search(aClass, GlobalSearchScope.allScope(aClass.getProject()));
  }

  public static Query<PsiClass> search(final PsiClass aClass, SearchScope scope) {
    return INSTANCE.createUniqueResultsQuery(new SearchParameters(aClass, scope));
  }

  public static Query<PsiClass> search(final PsiClass aClass, SearchScope scope, boolean includeAnonymous) {
    final Query<PsiClass> raw = INSTANCE.createUniqueResultsQuery(new SearchParameters(aClass, scope, includeAnonymous));

    if (!includeAnonymous) {
      return new FilteredQuery<PsiClass>(raw, new Condition<PsiClass>() {
        public boolean value(final PsiClass psiClass) {
          return !(psiClass instanceof PsiAnonymousClass);
        }
      });
    }

    return raw;
  }
}
