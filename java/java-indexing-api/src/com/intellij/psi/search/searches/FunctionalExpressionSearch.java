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
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFunctionalExpression;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.search.SearchScope;
import com.intellij.util.EmptyQuery;
import com.intellij.util.Query;
import com.intellij.util.QueryExecutor;
import org.jetbrains.annotations.NotNull;

public class FunctionalExpressionSearch extends ExtensibleQueryFactory<PsiFunctionalExpression, FunctionalExpressionSearch.SearchParameters> {
  public static ExtensionPointName<QueryExecutor> EP_NAME = ExtensionPointName.create("com.intellij.functionalInterfaceSearch");
  public static final FunctionalExpressionSearch INSTANCE = new FunctionalExpressionSearch();

  public static class SearchParameters {
    private final PsiClass myElementToSearch;
    private final SearchScope myScope;

    public SearchParameters(PsiClass aClass, SearchScope scope) {
      myElementToSearch = aClass;
      myScope = scope;
    }

    public PsiClass getElementToSearch() {
      return myElementToSearch;
    }

    @NotNull
    public SearchScope getEffectiveSearchScope () {
      SearchScope accessScope = PsiSearchHelper.SERVICE.getInstance(myElementToSearch.getProject()).getUseScope(myElementToSearch);
      return myScope.intersectWith(accessScope);
    }
  }

  public static Query<PsiFunctionalExpression> search(final PsiClass aClass, SearchScope scope) {
    return INSTANCE.createUniqueResultsQuery(new SearchParameters(aClass, scope));
  }

  public static Query<PsiFunctionalExpression> search(final PsiMethod psiMethod) {
    return search(psiMethod, GlobalSearchScope.allScope(psiMethod.getProject()));
  }

  public static Query<PsiFunctionalExpression> search(final PsiMethod psiMethod, SearchScope scope) {
    if (!psiMethod.hasModifierProperty(PsiModifier.STATIC) && !psiMethod.hasModifierProperty(PsiModifier.DEFAULT)) {
      return INSTANCE.createUniqueResultsQuery(new SearchParameters(psiMethod.getContainingClass(), scope));
    }

    return EmptyQuery.getEmptyQuery();
  }

  public static Query<PsiFunctionalExpression> search(final PsiClass aClass) {
    return search(aClass, GlobalSearchScope.allScope(aClass.getProject()));
  }
}
