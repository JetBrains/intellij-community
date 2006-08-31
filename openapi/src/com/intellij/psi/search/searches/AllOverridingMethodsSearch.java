package com.intellij.psi.search.searches;

import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.search.SearchScope;
import com.intellij.util.EmptyQuery;
import com.intellij.util.Query;
import com.intellij.util.QueryFactory;

/**
 * @author ven
 * Searches deeply for all overriding methods of all methods in a class, processing pairs
 * (method in original class, overriding method)
 */
public class AllOverridingMethodsSearch extends QueryFactory<Pair<PsiMethod, PsiMethod>, AllOverridingMethodsSearch.SearchParameters> {
  public static AllOverridingMethodsSearch INSTANCE = new AllOverridingMethodsSearch();
  private static EmptyQuery<Pair<PsiMethod, PsiMethod>> EMPTY = new EmptyQuery<Pair<PsiMethod, PsiMethod>>();

  public static class SearchParameters {
    private final PsiClass myClass;
    private final SearchScope myScope;

    public SearchParameters(final PsiClass aClass, SearchScope scope) {
      myClass = aClass;
      myScope = scope;
    }

    public PsiClass getPsiClass() {
      return myClass;
    }

    public SearchScope getScope() {
      return myScope;
    }
  }

  private AllOverridingMethodsSearch() {
  }

  public static Query<Pair<PsiMethod, PsiMethod>> search(final PsiClass aClass, SearchScope scope) {
    if (aClass.hasModifierProperty(PsiModifier.FINAL)) return AllOverridingMethodsSearch.EMPTY; // Optimization
    return INSTANCE.createUniqueResultsQuery(new SearchParameters(aClass, scope));
  }

  public static Query<Pair<PsiMethod, PsiMethod>> search(final PsiClass aClass) {
    return search(aClass, aClass.getUseScope());
  }
}
