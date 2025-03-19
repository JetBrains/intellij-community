// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.search.searches;

import com.intellij.psi.*;
import com.intellij.psi.search.SearchScope;
import com.intellij.util.Query;
import org.jetbrains.annotations.NotNull;

public final class ImplicitToStringSearch extends ExtensibleQueryFactory<PsiExpression, ImplicitToStringSearch.SearchParameters> {
  public static final ImplicitToStringSearch INSTANCE = new ImplicitToStringSearch();

  public static class SearchParameters {
    private final PsiMethod myTargetMethod;
    private final @NotNull SearchScope myScope;

    public SearchParameters(@NotNull PsiMethod targetMethod, @NotNull SearchScope scope) {
      myTargetMethod = targetMethod;
      myScope = scope;
    }

    public @NotNull PsiMethod getTargetMethod() {
      return myTargetMethod;
    }

    public @NotNull SearchScope getSearchScope() {
      return myScope;
    }
  }

  public static @NotNull Query<PsiExpression> search(@NotNull PsiMethod targetMethod, @NotNull SearchScope scope) {
    return INSTANCE.createUniqueResultsQuery(new SearchParameters(targetMethod, scope), SmartPointerManager::createPointer);
  }

  public static boolean isToStringMethod(@NotNull PsiElement element) {
    if (!(element instanceof PsiMethod method)) {
      return false;
    }
    if (!"toString".equals(method.getName())) {
      return false;
    }
    if (method.getParameters().length != 0) {
      return false;
    }
    PsiType returnType = method.getReturnType();
    return returnType != null && returnType.equalsToText(CommonClassNames.JAVA_LANG_STRING);
  }
}
