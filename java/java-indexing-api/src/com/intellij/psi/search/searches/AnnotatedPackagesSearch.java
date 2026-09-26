// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.search.searches;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.Query;
import org.jetbrains.annotations.NotNull;

public final class AnnotatedPackagesSearch extends ExtensibleQueryFactory<PsiPackage, AnnotatedPackagesSearch.Parameters> {
  public static final AnnotatedPackagesSearch INSTANCE = new AnnotatedPackagesSearch();

  public static class Parameters {
    private final PsiClass myAnnotationClass;
    private final SearchScope myScope;

    public Parameters(final PsiClass annotationClass, final SearchScope scope) {
      myAnnotationClass = annotationClass;
      myScope = scope;
    }

    public @NotNull PsiClass getAnnotationClass() {
      return myAnnotationClass;
    }

    public @NotNull SearchScope getScope() {
      return myScope;
    }
  }

  private AnnotatedPackagesSearch() {}

  public static @NotNull Query<PsiPackage> search(@NotNull PsiClass annotationClass, @NotNull SearchScope scope) {
    return INSTANCE.createQuery(new Parameters(annotationClass, scope));
  }

  public static @NotNull Query<PsiPackage> search(@NotNull PsiClass annotationClass) {
    return search(annotationClass, GlobalSearchScope.allScope(PsiUtilCore.getProjectInReadAction(annotationClass)));
  }
}