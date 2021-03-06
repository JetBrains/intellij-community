// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.psi.search.searches;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.EmptyQuery;
import com.intellij.util.Query;
import com.intellij.util.QueryExecutor;
import org.jetbrains.annotations.NotNull;

/**
 * @author ven
 * Searches deeply for all overriding methods of all methods in a class, processing pairs
 * (method in original class, overriding method)
 */
public final class AllOverridingMethodsSearch extends ExtensibleQueryFactory<Pair<PsiMethod, PsiMethod>, AllOverridingMethodsSearch.SearchParameters> {
  public static final ExtensionPointName<QueryExecutor<Pair<PsiMethod, PsiMethod>, AllOverridingMethodsSearch.SearchParameters>> EP_NAME = ExtensionPointName.create("com.intellij.allOverridingMethodsSearch");
  public static final AllOverridingMethodsSearch INSTANCE = new AllOverridingMethodsSearch();

  public static class SearchParameters {
    @NotNull private final PsiClass myClass;
    @NotNull private final SearchScope myScope;

    public SearchParameters(@NotNull PsiClass aClass, @NotNull SearchScope scope) {
      myClass = aClass;
      myScope = scope;
    }

    @NotNull
    public PsiClass getPsiClass() {
      return myClass;
    }

    @NotNull
    public SearchScope getScope() {
      return myScope;
    }
  }

  private AllOverridingMethodsSearch() {
    super(EP_NAME);
  }

  @NotNull
  public static Query<Pair<PsiMethod, PsiMethod>> search(@NotNull PsiClass aClass, @NotNull SearchScope scope) {
    if (aClass.hasModifierProperty(PsiModifier.FINAL)) return EmptyQuery.getEmptyQuery(); // Optimization
    return INSTANCE.createUniqueResultsQuery(new SearchParameters(aClass, scope));
  }

  @NotNull
  public static Query<Pair<PsiMethod, PsiMethod>> search(@NotNull PsiClass aClass) {
    return search(aClass, GlobalSearchScope.allScope(PsiUtilCore.getProjectInReadAction(aClass)));
  }
}
