// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.search.searches;

import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.EmptyQuery;
import com.intellij.util.Query;
import org.jetbrains.annotations.NotNull;

public final class FunctionalExpressionSearch extends ExtensibleQueryFactory<PsiFunctionalExpression, FunctionalExpressionSearch.SearchParameters> {
  private static final FunctionalExpressionSearch INSTANCE = new FunctionalExpressionSearch();

  public static class SearchParameters {
    private final PsiClass myElementToSearch;
    private final SearchScope myScope;

    public SearchParameters(@NotNull PsiClass aClass, @NotNull SearchScope scope) {
      myElementToSearch = aClass;
      myScope = scope;
    }

    public PsiClass getElementToSearch() {
      return myElementToSearch;
    }

    @NotNull
    public SearchScope getEffectiveSearchScope () {
      return myScope
        .intersectWith(PsiSearchHelper.getInstance(myElementToSearch.getProject()).getUseScope(myElementToSearch));
    }
  }

  public static Query<PsiFunctionalExpression> search(@NotNull final PsiClass aClass, @NotNull SearchScope scope) {
    return INSTANCE.createUniqueResultsQuery(new SearchParameters(aClass, scope), SmartPointerManager::createPointer);
  }

  public static Query<PsiFunctionalExpression> search(@NotNull final PsiMethod psiMethod) {
    return search(psiMethod, GlobalSearchScope.allScope(PsiUtilCore.getProjectInReadAction(psiMethod)));
  }

  public static Query<PsiFunctionalExpression> search(@NotNull final PsiMethod psiMethod, @NotNull final SearchScope scope) {
    return ReadAction.compute(() -> {
      if (!psiMethod.hasModifierProperty(PsiModifier.STATIC) && !psiMethod.hasModifierProperty(PsiModifier.DEFAULT)) {
        final PsiClass containingClass = psiMethod.getContainingClass();
        if (containingClass != null) {
          return INSTANCE.createUniqueResultsQuery(new SearchParameters(containingClass, scope));
        }
      }

      return EmptyQuery.getEmptyQuery();
    });
  }

  public static Query<PsiFunctionalExpression> search(@NotNull final PsiClass aClass) {
    return search(aClass, GlobalSearchScope.allScope(PsiUtilCore.getProjectInReadAction(aClass)));
  }
}
