// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.search.searches;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.MethodSignatureBackedByPsiMethod;
import com.intellij.util.Query;
import com.intellij.util.QueryExecutor;
import com.intellij.util.QueryParameters;
import com.intellij.util.UniqueResultsQuery;
import com.intellij.util.containers.HashingStrategy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class SuperMethodsSearch extends ExtensibleQueryFactory<MethodSignatureBackedByPsiMethod, SuperMethodsSearch.SearchParameters> {
  public static final ExtensionPointName<QueryExecutor> EP_NAME = ExtensionPointName.create("com.intellij.superMethodsSearch");
  private static final SuperMethodsSearch SUPER_METHODS_SEARCH_INSTANCE = new SuperMethodsSearch();

  public static class SearchParameters implements QueryParameters {
    private final PsiMethod myMethod;
    //null means any class would be matched
    private final @Nullable PsiClass myClass;
    private final boolean myCheckBases;
    private final boolean myAllowStaticMethod;
    private final boolean myJlsOnly;

    public SearchParameters(@NotNull PsiMethod method,
                            @Nullable PsiClass aClass,
                            final boolean checkBases,
                            final boolean allowStaticMethod) {
      this(method, aClass, checkBases, allowStaticMethod, false);
    }

    public SearchParameters(@NotNull PsiMethod method, @Nullable PsiClass aClass, boolean checkBases, boolean allowStaticMethod,
                            boolean jlsOnly) {
      myCheckBases = checkBases;
      myClass = aClass;
      myMethod = method;
      myAllowStaticMethod = allowStaticMethod;
      myJlsOnly = jlsOnly;
    }

    @Override
    public @Nullable Project getProject() {
      return myMethod.getProject();
    }

    @Override
    public boolean isQueryValid() {
      return myMethod.isValid() && (myClass == null || myClass.isValid());
    }

    public final boolean isCheckBases() {
      return myCheckBases;
    }

    public final @NotNull PsiMethod getMethod() {
      return myMethod;
    }

    public final @Nullable PsiClass getPsiClass() {
      return myClass;
    }

    public final boolean isAllowStaticMethod() {
      return myAllowStaticMethod;
    }

    /**
     * @return whether only Java Language Specification-compliant supers are needed, not EJB-like "logical" inheritance hierarchy
     */
    public boolean isJlsOnly() {
      return myJlsOnly;
    }

  }

  private SuperMethodsSearch() {
  }

  public static @NotNull Query<MethodSignatureBackedByPsiMethod> search(@NotNull PsiMethod derivedMethod,
                                                                        final @Nullable PsiClass psiClass,
                                                                        boolean checkBases,
                                                                        boolean allowStaticMethod) {
    return search(new SearchParameters(derivedMethod, psiClass, checkBases, allowStaticMethod));
  }

  public static @NotNull Query<MethodSignatureBackedByPsiMethod> search(@NotNull SearchParameters parameters) {
    return new UniqueResultsQuery<>(SUPER_METHODS_SEARCH_INSTANCE.createQuery(parameters), METHOD_BASED_HASHING_STRATEGY);
  }

  private static final HashingStrategy<MethodSignatureBackedByPsiMethod> METHOD_BASED_HASHING_STRATEGY =
    new HashingStrategy<MethodSignatureBackedByPsiMethod>() {
      @Override
      public int hashCode(@Nullable MethodSignatureBackedByPsiMethod signature) {
        return signature == null ? 0 : signature.getMethod().hashCode();
      }

      @Override
      public boolean equals(@Nullable MethodSignatureBackedByPsiMethod s1, @Nullable MethodSignatureBackedByPsiMethod s2) {
        return s1 == s2 || (s1 != null && s2 != null && s1.getMethod().equals(s2.getMethod()));
      }
    };

}
