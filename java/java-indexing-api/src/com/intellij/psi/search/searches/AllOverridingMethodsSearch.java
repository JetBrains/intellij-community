/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

/**
 * @author ven
 * Searches deeply for all overriding methods of all methods in a class, processing pairs
 * (method in original class, overriding method)
 */
public class AllOverridingMethodsSearch extends ExtensibleQueryFactory<Pair<PsiMethod, PsiMethod>, AllOverridingMethodsSearch.SearchParameters> {
  public static final ExtensionPointName<QueryExecutor> EP_NAME = ExtensionPointName.create("com.intellij.allOverridingMethodsSearch");
  public static final AllOverridingMethodsSearch INSTANCE = new AllOverridingMethodsSearch();

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
    if (aClass.hasModifierProperty(PsiModifier.FINAL)) return EmptyQuery.getEmptyQuery(); // Optimization
    return INSTANCE.createUniqueResultsQuery(new SearchParameters(aClass, scope));
  }

  public static Query<Pair<PsiMethod, PsiMethod>> search(final PsiClass aClass) {
    return search(aClass, GlobalSearchScope.allScope(PsiUtilCore.getProjectInReadAction(aClass)));
  }
}
