/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.Query;
import com.intellij.util.QueryExecutor;
import org.jetbrains.annotations.NotNull;

/**
 * @author max
 */
public class DirectClassInheritorsSearch extends ExtensibleQueryFactory<PsiClass, DirectClassInheritorsSearch.SearchParameters> {
  public static final ExtensionPointName<QueryExecutor> EP_NAME = ExtensionPointName.create("com.intellij.directClassInheritorsSearch");
  public static final DirectClassInheritorsSearch INSTANCE = new DirectClassInheritorsSearch();

  public static class SearchParameters {
    @NotNull private final PsiClass myClass;
    @NotNull private final SearchScope myScope;
    private final boolean myIncludeAnonymous;
    private final boolean myCheckInheritance;

    public SearchParameters(@NotNull PsiClass aClass, @NotNull SearchScope scope, boolean includeAnonymous, boolean checkInheritance) {
      myClass = aClass;
      myScope = scope;
      myIncludeAnonymous = includeAnonymous;
      myCheckInheritance = checkInheritance;
    }

    public SearchParameters(@NotNull PsiClass aClass, @NotNull SearchScope scope, final boolean includeAnonymous) {
      this(aClass, scope, includeAnonymous, true);
    }

    public SearchParameters(@NotNull PsiClass aClass, @NotNull SearchScope scope) {
      this(aClass, scope, true);
    }

    @NotNull
    public PsiClass getClassToProcess() {
      return myClass;
    }

    @NotNull
    public SearchScope getScope() {
      return myScope;
    }

    public boolean isCheckInheritance() {
      return myCheckInheritance;
    }

    public boolean includeAnonymous() {
      return myIncludeAnonymous;
    }
  }

  private DirectClassInheritorsSearch() {
  }

  @NotNull
  public static Query<PsiClass> search(@NotNull PsiClass aClass) {
    return search(aClass, GlobalSearchScope.allScope(PsiUtilCore.getProjectInReadAction(aClass)));
  }

  @NotNull
  public static Query<PsiClass> search(@NotNull PsiClass aClass, @NotNull SearchScope scope) {
    return search(aClass, scope, true);
  }

  @NotNull
  public static Query<PsiClass> search(@NotNull PsiClass aClass, @NotNull SearchScope scope, boolean includeAnonymous) {
    return INSTANCE.createUniqueResultsQuery(new SearchParameters(aClass, scope, includeAnonymous, true));
  }

  /**
   * @deprecated use {@link #search(PsiClass, SearchScope, boolean)} instead
   */
  @NotNull
  @Deprecated
  public static Query<PsiClass> search(@NotNull PsiClass aClass,
                                       @NotNull SearchScope scope,
                                       boolean includeAnonymous,
                                       final boolean checkInheritance) {
    return search(aClass, scope, includeAnonymous);
  }
}
