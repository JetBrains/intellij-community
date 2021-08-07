// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.search.searches;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.MergeQuery;
import com.intellij.util.Query;
import org.jetbrains.annotations.NotNull;

public final class AnnotationTargetsSearch {
  public static AnnotationTargetsSearch INSTANCE = new AnnotationTargetsSearch();

  public static class Parameters {
    private final PsiClass myAnnotationClass;
    private final SearchScope myScope;

    public Parameters(@NotNull PsiClass annotationClass, @NotNull SearchScope scope) {
      myAnnotationClass = annotationClass;
      myScope = scope;
    }

    @NotNull
    public PsiClass getAnnotationClass() {
      return myAnnotationClass;
    }

    @NotNull
    public SearchScope getScope() {
      return myScope;
    }
  }

  private AnnotationTargetsSearch() {}

  @NotNull
  public static Query<PsiModifierListOwner> search(@NotNull PsiClass annotationClass, @NotNull SearchScope scope) {
    final Query<PsiMember> members = AnnotatedMembersSearch.search(annotationClass, scope);
    final Query<PsiPackage> packages = AnnotatedPackagesSearch.search(annotationClass, scope);
    return new MergeQuery<>(members, packages);
  }

  @NotNull
  public static Query<PsiModifierListOwner> search(@NotNull PsiClass annotationClass) {
    return search(annotationClass, GlobalSearchScope.allScope(PsiUtilCore.getProjectInReadAction(annotationClass)));
  }
}