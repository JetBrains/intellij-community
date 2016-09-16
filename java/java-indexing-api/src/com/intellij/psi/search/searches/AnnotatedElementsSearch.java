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
import com.intellij.psi.*;
import com.intellij.psi.search.SearchScope;
import com.intellij.util.InstanceofQuery;
import com.intellij.util.Query;
import com.intellij.util.QueryExecutor;
import org.jetbrains.annotations.NotNull;

public class AnnotatedElementsSearch extends ExtensibleQueryFactory<PsiModifierListOwner, AnnotatedElementsSearch.Parameters> {
  public static final ExtensionPointName<QueryExecutor> EP_NAME = ExtensionPointName.create("com.intellij.annotatedElementsSearch");
  public static final AnnotatedElementsSearch INSTANCE = new AnnotatedElementsSearch();

  public static class Parameters {
    private final PsiClass myAnnotationClass;
    private final SearchScope myScope;
    private final Class<? extends PsiModifierListOwner>[] myTypes;
    private final boolean myApproximate;

    public Parameters(final PsiClass annotationClass, final SearchScope scope, Class<? extends PsiModifierListOwner>... types) {
      this(annotationClass, scope, false, types);
    }

    public Parameters(final PsiClass annotationClass, final SearchScope scope, boolean approximate, Class<? extends PsiModifierListOwner>... types) {
      myAnnotationClass = annotationClass;
      myScope = scope;
      myTypes = types;
      myApproximate = approximate;
    }

    public PsiClass getAnnotationClass() {
      return myAnnotationClass;
    }

    public SearchScope getScope() {
      return myScope;
    }

    public Class<? extends PsiModifierListOwner>[] getTypes() {
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

  public static <T extends PsiModifierListOwner> Query<T> searchElements(@NotNull PsiClass annotationClass, @NotNull SearchScope scope, Class<? extends T>... types) {
    //noinspection unchecked
    return (Query<T>)searchElements(new Parameters(annotationClass, scope, types));
  }

  @NotNull
  public static Query<? extends PsiModifierListOwner> searchElements(Parameters parameters) {
    return new InstanceofQuery<>(INSTANCE.createQuery(parameters), parameters.getTypes());
  }

  public static Query<PsiClass> searchPsiClasses(@NotNull PsiClass annotationClass, @NotNull SearchScope scope) {
     return searchElements(annotationClass, scope, PsiClass.class);
  }

  public static Query<PsiMethod> searchPsiMethods(@NotNull PsiClass annotationClass, @NotNull SearchScope scope) {
    return searchElements(annotationClass, scope, PsiMethod.class);
  }

  public static Query<PsiMember> searchPsiMembers(@NotNull PsiClass annotationClass, @NotNull SearchScope scope) {
    return searchElements(annotationClass, scope, PsiMember.class);
  }

  public static Query<PsiField> searchPsiFields(@NotNull PsiClass annotationClass, @NotNull SearchScope scope) {
    return searchElements(annotationClass, scope, PsiField.class);
  }

  public static Query<PsiParameter> searchPsiParameters(@NotNull PsiClass annotationClass, @NotNull SearchScope scope) {
    return searchElements(annotationClass, scope, PsiParameter.class);
  }
}
