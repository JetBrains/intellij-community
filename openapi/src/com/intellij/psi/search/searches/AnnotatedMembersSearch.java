/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.psi.search.searches;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMember;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.util.Query;
import com.intellij.util.QueryFactory;
import org.jetbrains.annotations.NotNull;

/**
 * @author max
 */
public class AnnotatedMembersSearch extends QueryFactory<PsiMember, AnnotatedMembersSearch.Parameters> {
  public static AnnotatedMembersSearch INSTANCE = new AnnotatedMembersSearch();

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

  private AnnotatedMembersSearch() {}

  public static Query<PsiMember> search(@NotNull PsiClass annotationClass, @NotNull SearchScope scope) {
    return INSTANCE.createQuery(new Parameters(annotationClass, scope));
  }

  public static Query<PsiMember> search(@NotNull PsiClass annotationClass) {
    return search(annotationClass, GlobalSearchScope.allScope(annotationClass.getProject()));
  }
}
