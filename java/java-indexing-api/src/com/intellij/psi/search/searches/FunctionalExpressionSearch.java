// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.search.searches;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.EmptyQuery;
import com.intellij.util.Query;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class FunctionalExpressionSearch extends ExtensibleQueryFactory<PsiFunctionalExpression, FunctionalExpressionSearch.SearchParameters> {
  private static final FunctionalExpressionSearch INSTANCE = new FunctionalExpressionSearch();

  public static class SearchParameters {
    private final PsiClass myElementToSearch;
    private final PsiMethod myMethod;
    private final SearchScope myScope;
    private final @NotNull Project myProject;

    public SearchParameters(@NotNull PsiClass aClass,
                        @NotNull SearchScope scope) {
      this(aClass, null, scope);
    }

    public SearchParameters(@NotNull PsiClass aClass,
                            @Nullable PsiMethod psiMethod,
                            @NotNull SearchScope scope) {
      myProject = aClass.getProject();
      myElementToSearch = aClass;
      myMethod = psiMethod;
      myScope = scope;
    }

    @Nullable
    public PsiMethod getMethod() {
      return myMethod;
    }

    @NotNull
    public PsiClass getElementToSearch() {
      return myElementToSearch;
    }

    @NotNull
    public SearchScope getEffectiveSearchScope () {
      return myScope.intersectWith(PsiSearchHelper.getInstance(myProject).getUseScope(myElementToSearch));
    }

    @NotNull
    public Project getProject() {
      return myProject;
    }
  }

  @NotNull
  public static Query<PsiFunctionalExpression> search(@NotNull PsiClass aClass, @NotNull SearchScope scope) {
    SearchParameters parameters = ReadAction.compute(() -> new SearchParameters(aClass, scope));
    return INSTANCE.createUniqueResultsQuery(parameters, element -> ReadAction.compute(()->SmartPointerManager.getInstance(parameters.myProject).createSmartPsiElementPointer(element)));
  }

  @NotNull
  public static Query<PsiFunctionalExpression> search(@NotNull PsiMethod psiMethod) {
    return search(psiMethod, GlobalSearchScope.allScope(PsiUtilCore.getProjectInReadAction(psiMethod)));
  }

  @NotNull
  public static Query<PsiFunctionalExpression> search(@NotNull PsiMethod psiMethod, @NotNull SearchScope scope) {
    return ReadAction.compute(() -> {
      if (psiMethod.hasModifierProperty(PsiModifier.ABSTRACT)) {
        PsiClass containingClass = psiMethod.getContainingClass();
        if (containingClass != null) {
          return INSTANCE.createUniqueResultsQuery(new SearchParameters(containingClass, psiMethod, scope));
        }
      }
      return EmptyQuery.getEmptyQuery();
    });
  }

  @NotNull
  public static Query<PsiFunctionalExpression> search(@NotNull PsiClass aClass) {
    return search(aClass, GlobalSearchScope.allScope(PsiUtilCore.getProjectInReadAction(aClass)));
  }
}
