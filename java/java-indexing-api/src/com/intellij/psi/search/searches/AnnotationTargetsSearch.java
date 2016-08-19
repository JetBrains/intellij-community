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
    return new MergeQuery<>(members, packages);
  }

  public static Query<PsiModifierListOwner> search(@NotNull PsiClass annotationClass) {
    return search(annotationClass, GlobalSearchScope.allScope(PsiUtilCore.getProjectInReadAction(annotationClass)));
  }
}