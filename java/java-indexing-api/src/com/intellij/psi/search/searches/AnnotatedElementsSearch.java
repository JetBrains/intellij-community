// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.search.searches;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.*;
import com.intellij.psi.search.SearchScope;
import com.intellij.util.InstanceofQuery;
import com.intellij.util.Query;
import com.intellij.util.QueryExecutor;
import org.jetbrains.annotations.NotNull;

public final class AnnotatedElementsSearch extends ExtensibleQueryFactory<PsiModifierListOwner, AnnotatedElementsSearch.Parameters> {
  public static final ExtensionPointName<QueryExecutor<PsiModifierListOwner, AnnotatedElementsSearch.Parameters>> EP_NAME = ExtensionPointName.create("com.intellij.annotatedElementsSearch");
  public static final AnnotatedElementsSearch INSTANCE = new AnnotatedElementsSearch();

  public static class Parameters {
    private final PsiClass myAnnotationClass;
    private final SearchScope myScope;
    private final Class<? extends PsiModifierListOwner>[] myTypes;
    private final boolean myApproximate;

    @SafeVarargs
    public Parameters(@NotNull PsiClass annotationClass, @NotNull SearchScope scope, @NotNull Class<? extends PsiModifierListOwner> @NotNull ... types) {
      this(annotationClass, scope, false, types);
    }

    @SafeVarargs
    public Parameters(@NotNull PsiClass annotationClass, @NotNull SearchScope scope, boolean approximate, @NotNull Class<? extends PsiModifierListOwner> @NotNull ... types) {
      myAnnotationClass = annotationClass;
      myScope = scope;
      myTypes = types;
      myApproximate = approximate;
    }

    @NotNull
    public PsiClass getAnnotationClass() {
      return myAnnotationClass;
    }

    @NotNull
    public SearchScope getScope() {
      return myScope;
    }

    @NotNull
    public Class<? extends PsiModifierListOwner> @NotNull [] getTypes() {
      return myTypes;
    }

    /**
     * @return whether searchers may return a superset of the annotations being requested (e.g. all with the same short name) and
     * avoid expensive resolve operations
     */
    public boolean isApproximate() {
      return myApproximate;
    }
  }

  private AnnotatedElementsSearch() {
    super(EP_NAME);
  }

  @NotNull
  @SafeVarargs
  public static <T extends PsiModifierListOwner> Query<T> searchElements(@NotNull PsiClass annotationClass, @NotNull SearchScope scope, @NotNull Class<? extends T> @NotNull ... types) {
    //noinspection unchecked
    return (Query<T>)searchElements(new Parameters(annotationClass, scope, types));
  }

  @NotNull
  public static Query<? extends PsiModifierListOwner> searchElements(@NotNull Parameters parameters) {
    return new InstanceofQuery<>(INSTANCE.createQuery(parameters), parameters.getTypes());
  }

  @NotNull
  public static Query<PsiClass> searchPsiClasses(@NotNull PsiClass annotationClass, @NotNull SearchScope scope) {
     return searchElements(annotationClass, scope, PsiClass.class);
  }

  @NotNull
  public static Query<PsiMethod> searchPsiMethods(@NotNull PsiClass annotationClass, @NotNull SearchScope scope) {
    return searchElements(annotationClass, scope, PsiMethod.class);
  }

  @NotNull
  public static Query<PsiMember> searchPsiMembers(@NotNull PsiClass annotationClass, @NotNull SearchScope scope) {
    return searchElements(annotationClass, scope, PsiMember.class);
  }

  @NotNull
  public static Query<PsiField> searchPsiFields(@NotNull PsiClass annotationClass, @NotNull SearchScope scope) {
    return searchElements(annotationClass, scope, PsiField.class);
  }

  @NotNull
  public static Query<PsiParameter> searchPsiParameters(@NotNull PsiClass annotationClass, @NotNull SearchScope scope) {
    return searchElements(annotationClass, scope, PsiParameter.class);
  }
}
