// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.search.searches;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.*;
import com.intellij.psi.search.SearchScope;
import com.intellij.util.Query;
import com.intellij.util.QueryExecutor;
import org.jetbrains.annotations.NotNull;

/**
 * Searcher that searches for classes which have members annotated with the specified annotation.
 *
 * @author yole
 */
public final class ClassesWithAnnotatedMembersSearch extends ExtensibleQueryFactory<PsiClass, ClassesWithAnnotatedMembersSearch.Parameters> {
  public static final ExtensionPointName<QueryExecutor<PsiClass, ClassesWithAnnotatedMembersSearch.Parameters>> EP_NAME = ExtensionPointName.create("com.intellij.classesWithAnnotatedMembersSearch");
  public static final ClassesWithAnnotatedMembersSearch INSTANCE = new ClassesWithAnnotatedMembersSearch();

  private ClassesWithAnnotatedMembersSearch() {
    super(EP_NAME);
  }

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

  public static Query<PsiClass> search(@NotNull PsiClass annotationClass, @NotNull SearchScope scope) {
    return INSTANCE.createQuery(new Parameters(annotationClass, scope));
  }
}
