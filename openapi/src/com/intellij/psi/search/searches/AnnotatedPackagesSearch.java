/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.psi.search.searches;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.util.Query;
import org.jetbrains.annotations.NotNull;

/**
 * @author max
 */
public class AnnotatedPackagesSearch extends ExtensibleQueryFactory<PsiPackage, AnnotatedPackagesSearch.Parameters> {
  public static AnnotatedPackagesSearch INSTANCE = new AnnotatedPackagesSearch();

  public static class Parameters {
    private final PsiClass myAnnotationClass;
    private final SearchScope myScope;

    public Parameters(final PsiClass annotationClass, final SearchScope scope) {
      myAnnotationClass = annotationClass;
      myScope = scope;
    }

    public PsiClass getAnnotationClass() {
      return myAnnotationClass;
    }

    public SearchScope getScope() {
      return myScope;
    }
  }

  private AnnotatedPackagesSearch() {}

  public static Query<PsiPackage> search(@NotNull PsiClass annotationClass, @NotNull SearchScope scope) {
    return INSTANCE.createQuery(new Parameters(annotationClass, scope));
  }

  public static Query<PsiPackage> search(@NotNull PsiClass annotationClass) {
    return search(annotationClass, GlobalSearchScope.allScope(annotationClass.getProject()));
  }
}