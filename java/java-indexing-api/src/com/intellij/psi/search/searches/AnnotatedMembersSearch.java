// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.search.searches;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMember;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.Query;
import org.jetbrains.annotations.NotNull;

public final class AnnotatedMembersSearch {

  private AnnotatedMembersSearch() {}

  public static Query<PsiMember> search(@NotNull PsiClass annotationClass, @NotNull SearchScope scope) {
    return AnnotatedElementsSearch.searchPsiMembers(annotationClass, scope);
  }

  public static Query<PsiMember> search(@NotNull PsiClass annotationClass) {
    return search(annotationClass, GlobalSearchScope.allScope(PsiUtilCore.getProjectInReadAction(annotationClass)));
  }
}
