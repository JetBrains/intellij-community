/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.psi.search.searches;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.util.MergeQuery;
import com.intellij.util.Query;
import org.jetbrains.annotations.NotNull;

/**
 * @author max
 */
public class AnnotationTargetsSearch {
  public static AnnotationTargetsSearch INSTANCE = new AnnotationTargetsSearch();

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

  private AnnotationTargetsSearch() {}

  public static Query<PsiModifierListOwner> search(@NotNull PsiClass annotationClass, @NotNull SearchScope scope) {
    final Query<PsiMember> members = AnnotatedMembersSearch.search(annotationClass, scope);
    final Query<PsiPackage> packages = AnnotatedPackagesSearch.search(annotationClass, scope);
    return new MergeQuery<PsiModifierListOwner, PsiMember, PsiPackage>(members, packages);
  }

  public static Query<PsiModifierListOwner> search(@NotNull PsiClass annotationClass) {
    return search(annotationClass, GlobalSearchScope.allScope(annotationClass.getProject()));
  }
}