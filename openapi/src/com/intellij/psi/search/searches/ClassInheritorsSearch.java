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
public class ClassInheritorsSearch extends QueryFactory<PsiClass, ClassInheritorsSearch.SearchParameters> {
  public static ClassInheritorsSearch INSTANCE = new ClassInheritorsSearch();

  public static class SearchParameters {
    private final PsiClass myClass;
    private final SearchScope myScope;
    private final boolean myCheckDeep;

    public SearchParameters(final PsiClass aClass, SearchScope scope, final boolean checkDeep) {
      myClass = aClass;
      myScope = scope;
      myCheckDeep = checkDeep;
    }

    public PsiClass getClassToProcess() {
      return myClass;
    }

    public boolean isCheckDeep() {
      return myCheckDeep;
    }

    public SearchScope getScope() {
      return myScope;
    }
  }

  private ClassInheritorsSearch() {}

  public static Query<PsiClass> search(final PsiClass aClass, SearchScope scope, final boolean checkDeep) {
    return INSTANCE.createQuery(new SearchParameters(aClass, scope, checkDeep));
  }

  public static Query<PsiClass> search(final PsiClass aClass, final boolean checkDeep) {
    return search(aClass, GlobalSearchScope.allScope(aClass.getProject()), checkDeep);
  }

  public static Query<PsiClass> search(final PsiClass aClass) {
    return search(aClass, true);
  }
}
