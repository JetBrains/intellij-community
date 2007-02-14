/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.psi.search.searches;

import com.intellij.psi.PsiMethod;
import com.intellij.util.Query;

/**
 * @author max
 */
public class DeepestSuperMethodsSearch extends ExtensibleQueryFactory<PsiMethod, PsiMethod> {
  public static DeepestSuperMethodsSearch DEEPEST_SUPER_METHODS_SEARCH_INSTANCE = new DeepestSuperMethodsSearch();

  private DeepestSuperMethodsSearch() {
  }

  public static Query<PsiMethod> search(PsiMethod method) {
    return DEEPEST_SUPER_METHODS_SEARCH_INSTANCE.createQuery(method);
  }

}
