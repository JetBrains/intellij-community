/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.psi.search.searches;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.util.EmptyQuery;
import com.intellij.util.Query;
import com.intellij.util.QueryFactory;

/**
 * @author max
 */
public class SuperMethodsSearch extends QueryFactory<PsiMethod, SuperMethodsSearch.SearchParameters> {
  public static SuperMethodsSearch SUPER_METHODS_SEARCH_INSTANCE = new SuperMethodsSearch();
  private static EmptyQuery<PsiMethod> EMPTY = new EmptyQuery<PsiMethod>();

  public static class SearchParameters {
    private final PsiMethod myMethod;
    private final PsiClass myClass;
    private final PsiSubstitutor mySubstitutor;
    private final boolean myCheckBases;

    public SearchParameters(final PsiMethod method, PsiSubstitutor substitutor, final PsiClass aClass, final boolean checkBases) {
      mySubstitutor = substitutor;
      myCheckBases = checkBases;
      myClass = aClass;
      myMethod = method;
    }

    public boolean isCheckBases() {
      return myCheckBases;
    }

    public PsiMethod getMethod() {
      return myMethod;
    }

    public PsiClass getPsiClass() {
      return myClass;
    }

    public PsiSubstitutor getSubstitutor() {
      return mySubstitutor;
    }
  }

  private SuperMethodsSearch() {
  }

  public static Query<PsiMethod> search(final PsiMethod derivedMethod, PsiSubstitutor substitutor, final PsiClass psiClass, boolean checkBases) {
    if (cannotBeOverridding(derivedMethod)) return SuperMethodsSearch.EMPTY; // Optimization
    return SUPER_METHODS_SEARCH_INSTANCE.createQuery(new SuperMethodsSearch.SearchParameters(derivedMethod, substitutor, psiClass, checkBases));
  }

  private static boolean cannotBeOverridding(final PsiMethod method) {
    final PsiClass parentClass = method.getContainingClass();
    return parentClass == null
        || method.isConstructor()
        || method.hasModifierProperty(PsiModifier.STATIC)
        || method.hasModifierProperty(PsiModifier.PRIVATE);
  }

}
