// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.search.searches;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.EmptyQuery;
import com.intellij.util.Query;
import com.intellij.util.QueryExecutor;
import org.jetbrains.annotations.NotNull;

public final class OverridingMethodsSearch extends ExtensibleQueryFactory<PsiMethod, OverridingMethodsSearch.SearchParameters> {
  public static final ExtensionPointName<QueryExecutor<PsiMethod, OverridingMethodsSearch.SearchParameters>> EP_NAME = ExtensionPointName.create("com.intellij.overridingMethodsSearch");
  public static final OverridingMethodsSearch INSTANCE = new OverridingMethodsSearch();

  public static class SearchParameters {
    private final @NotNull PsiMethod myMethod;
    private final @NotNull SearchScope myScope;
    private final boolean myCheckDeep;

    public SearchParameters(@NotNull PsiMethod method, @NotNull SearchScope scope, final boolean checkDeep) {
      myMethod = method;
      myScope = scope;
      myCheckDeep = checkDeep;
    }

    public @NotNull PsiMethod getMethod() {
      return myMethod;
    }

    public boolean isCheckDeep() {
      return myCheckDeep;
    }

    public @NotNull SearchScope getScope() {
      return myScope;
    }
  }

  private OverridingMethodsSearch() {
    super(EP_NAME);
  }

  /**
   * @param checkDeep false means that processing would be stopped after the first found item
   *                  Because search is done in parallel, it can happen that multiple items would be actually found
   */
  public static @NotNull Query<PsiMethod> search(@NotNull PsiMethod method, @NotNull SearchScope scope, final boolean checkDeep) {
    if (ReadAction.compute(() -> !PsiUtil.canBeOverridden(method))) return EmptyQuery.getEmptyQuery(); // Optimization
    return INSTANCE.createUniqueResultsQuery(new SearchParameters(method, scope, checkDeep));
  }

  /**
   * @param method base method
   * @param checkDeep if true, indirect overrides will also be returned
   * @return query containing methods that override the base method
   */
  public static @NotNull Query<PsiMethod> search(@NotNull PsiMethod method, final boolean checkDeep) {
    return search(method, ReadAction.compute(method::getUseScope), checkDeep);
  }

  /**
   * @param method base method
   * @return query containing methods that override the base method (directly or indirectly)
   */
  public static @NotNull Query<PsiMethod> search(@NotNull PsiMethod method) {
    return search(method, true);
  }
}
