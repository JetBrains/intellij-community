/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

/**
 * @author max
 */
public class OverridingMethodsSearch extends ExtensibleQueryFactory<PsiMethod, OverridingMethodsSearch.SearchParameters> {
  public static final ExtensionPointName<QueryExecutor> EP_NAME = ExtensionPointName.create("com.intellij.overridingMethodsSearch");
  public static final OverridingMethodsSearch INSTANCE = new OverridingMethodsSearch();

  public static class SearchParameters {
    @NotNull private final PsiMethod myMethod;
    @NotNull private final SearchScope myScope;
    private final boolean myCheckDeep;

    public SearchParameters(@NotNull PsiMethod method, @NotNull SearchScope scope, final boolean checkDeep) {
      myMethod = method;
      myScope = scope;
      myCheckDeep = checkDeep;
    }

    @NotNull
    public PsiMethod getMethod() {
      return myMethod;
    }

    public boolean isCheckDeep() {
      return myCheckDeep;
    }

    @NotNull
    public SearchScope getScope() {
      return myScope;
    }
  }

  private OverridingMethodsSearch() {
  }

  public static Query<PsiMethod> search(@NotNull PsiMethod method, @NotNull SearchScope scope, final boolean checkDeep) {
    if (ReadAction.compute(() -> !PsiUtil.canBeOverridden(method))) return EmptyQuery.getEmptyQuery(); // Optimization
    return INSTANCE.createUniqueResultsQuery(new SearchParameters(method, scope, checkDeep));
  }

  @NotNull
  public static Query<PsiMethod> search(@NotNull PsiMethod method, final boolean checkDeep) {
    return search(method, ReadAction.compute(method::getUseScope), checkDeep);
  }

  @NotNull
  public static Query<PsiMethod> search(@NotNull PsiMethod method) {
    return search(method, true);
  }
}
