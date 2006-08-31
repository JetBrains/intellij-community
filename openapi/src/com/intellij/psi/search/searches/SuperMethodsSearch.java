/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.psi.search.searches;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.MethodSignatureBackedByPsiMethod;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.util.Query;
import com.intellij.util.QueryFactory;
import org.jetbrains.annotations.Nullable;

/**
 * @author max
 */
public class SuperMethodsSearch extends QueryFactory<MethodSignatureBackedByPsiMethod, SuperMethodsSearch.SearchParameters> {
  public static SuperMethodsSearch SUPER_METHODS_SEARCH_INSTANCE = new SuperMethodsSearch();

  public static class SearchParameters {
    private final PsiMethod myMethod;
    //null means any class would be matched
    @Nullable private final PsiClass myClass;
    private final boolean myCheckBases;
    private final boolean myAllowStaticMethod;

    public SearchParameters(final PsiMethod method,
                            @Nullable final PsiClass aClass,
                            final boolean checkBases,
                            final boolean allowStaticMethod) {
      myCheckBases = checkBases;
      myClass = aClass;
      myMethod = method;
      myAllowStaticMethod = allowStaticMethod;
    }

    public final boolean isCheckBases() {
      return myCheckBases;
    }

    public final PsiMethod getMethod() {
      return myMethod;
    }

    @Nullable
    public final PsiClass getPsiClass() {
      return myClass;
    }

    public final boolean isAllowStaticMethod() {
      return myAllowStaticMethod;
    }
  }

  private SuperMethodsSearch() {
  }

  public static Query<MethodSignatureBackedByPsiMethod> search(final PsiMethod derivedMethod, final PsiClass psiClass, boolean checkBases, boolean allowStaticMethod) {
    final SearchParameters parameters = new SearchParameters(derivedMethod, psiClass, checkBases, allowStaticMethod);
    return SUPER_METHODS_SEARCH_INSTANCE.createUniqueResultsQuery(parameters, MethodSignatureUtil.METHOD_BASED_HASHING_STRATEGY);
  }

}
