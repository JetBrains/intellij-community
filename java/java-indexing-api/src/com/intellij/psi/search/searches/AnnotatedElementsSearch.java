// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.search.searches;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.search.SearchScope;
import com.intellij.util.InstanceofQuery;
import com.intellij.util.Query;
import com.intellij.util.QueryExecutor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class AnnotatedElementsSearch extends ExtensibleQueryFactory<PsiModifierListOwner, AnnotatedElementsSearch.Parameters> {
  public static final ExtensionPointName<QueryExecutor<PsiModifierListOwner, AnnotatedElementsSearch.Parameters>> EP_NAME = ExtensionPointName.create("com.intellij.annotatedElementsSearch");
  public static final AnnotatedElementsSearch INSTANCE = new AnnotatedElementsSearch();

  public static class Parameters {
    private final @Nullable PsiClass myAnnotationClass;
    private final @Nullable String myAnnotationName;
    private final @NotNull Project myProject;
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
      myAnnotationName = null;
      myProject = myAnnotationClass.getProject();
      myScope = scope;
      myTypes = types;
      myApproximate = approximate;
    }

    /**
     * Searches for elements annotated with an annotation of the given fully qualified name, regardless of which particular
     * {@link PsiClass} that name resolves to. This is important when the same annotation is present on the classpath in
     * several jars (e.g. different library versions): matching by name rather than by resolved class makes the result
     * deterministic and independent of the resolve outcome.
     */
    @SafeVarargs
    public Parameters(@NotNull Project project, @NotNull String annotationFQN, @NotNull SearchScope scope, boolean approximate,
                      @NotNull Class<? extends PsiModifierListOwner> @NotNull ... types) {
      myAnnotationClass = null;
      myAnnotationName = annotationFQN;
      myProject = project;
      myScope = scope;
      myTypes = types;
      myApproximate = approximate;
    }

    /**
     * @return the annotation class the search was set up with, or {@code null} if it was set up with a bare fully qualified name
     * (see {@link #getAnnotationName()}).
     */
    public @Nullable PsiClass getAnnotationClass() {
      return myAnnotationClass;
    }

    /**
     * @return the fully qualified name the search was set up with, or {@code null} if it was set up with a {@link PsiClass}
     * (in which case the name should be derived from {@link #getAnnotationClass()}).
     */
    public @Nullable String getAnnotationName() {
      return myAnnotationName;
    }

    public @NotNull Project getProject() {
      return myProject;
    }

    public @NotNull SearchScope getScope() {
      return myScope;
    }

    public @NotNull Class<? extends PsiModifierListOwner> @NotNull [] getTypes() {
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

  @SafeVarargs
  public static @NotNull <T extends PsiModifierListOwner> Query<T> searchElements(@NotNull PsiClass annotationClass,
                                                                                  @NotNull SearchScope scope,
                                                                                  @NotNull Class<? extends T> @NotNull ... types) {
    //noinspection unchecked
    return (Query<T>)searchElements(new Parameters(annotationClass, scope, types));
  }

  public static @NotNull Query<? extends PsiModifierListOwner> searchElements(@NotNull Parameters parameters) {
    return new InstanceofQuery<>(INSTANCE.createQuery(parameters), parameters.getTypes());
  }

  public static @NotNull Query<PsiClass> searchPsiClasses(@NotNull PsiClass annotationClass, @NotNull SearchScope scope) {
     return searchElements(annotationClass, scope, PsiClass.class);
  }

  /**
   * Searches for classes annotated with an annotation of the given fully qualified name, regardless of which particular
   * {@link PsiClass} that name resolves to (and even if it does not resolve to any class present in the project).
   *
   * @see Parameters#Parameters(Project, String, SearchScope, boolean, Class[])
   */
  public static @NotNull Query<PsiClass> searchPsiClasses(@NotNull Project project, @NotNull String annotationFQN, @NotNull SearchScope scope) {
    //noinspection unchecked
    return (Query<PsiClass>)searchElements(new Parameters(project, annotationFQN, scope, false, PsiClass.class));
  }

  public static @NotNull Query<PsiMethod> searchPsiMethods(@NotNull PsiClass annotationClass, @NotNull SearchScope scope) {
    return searchElements(annotationClass, scope, PsiMethod.class);
  }

  public static @NotNull Query<PsiMember> searchPsiMembers(@NotNull PsiClass annotationClass, @NotNull SearchScope scope) {
    return searchElements(annotationClass, scope, PsiMember.class);
  }

  public static @NotNull Query<PsiField> searchPsiFields(@NotNull PsiClass annotationClass, @NotNull SearchScope scope) {
    return searchElements(annotationClass, scope, PsiField.class);
  }

  public static @NotNull Query<PsiParameter> searchPsiParameters(@NotNull PsiClass annotationClass, @NotNull SearchScope scope) {
    return searchElements(annotationClass, scope, PsiParameter.class);
  }
}
