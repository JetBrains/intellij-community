// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.search.searches;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiMethod;
import com.intellij.util.Query;
import com.intellij.util.QueryExecutor;

public final class DeepestSuperMethodsSearch extends ExtensibleQueryFactory<PsiMethod, PsiMethod> {
  public static final ExtensionPointName<QueryExecutor<PsiMethod, PsiMethod>> EP_NAME = ExtensionPointName.create("com.intellij.deepestSuperMethodsSearch");
  public static final DeepestSuperMethodsSearch DEEPEST_SUPER_METHODS_SEARCH_INSTANCE = new DeepestSuperMethodsSearch();

  private DeepestSuperMethodsSearch() {
    super(EP_NAME);
  }

  public static Query<PsiMethod> search(PsiMethod method) {
    return DEEPEST_SUPER_METHODS_SEARCH_INSTANCE.createQuery(method);
  }
}
