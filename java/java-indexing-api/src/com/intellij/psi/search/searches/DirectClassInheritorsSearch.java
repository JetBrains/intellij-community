// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.search.searches;

import com.intellij.lang.Language;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.Query;
import com.intellij.util.QueryExecutor;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

public final class DirectClassInheritorsSearch extends ExtensibleQueryFactory<PsiClass, DirectClassInheritorsSearch.SearchParameters> {
  public static final ExtensionPointName<QueryExecutor<PsiClass, DirectClassInheritorsSearch.SearchParameters>> EP_NAME = ExtensionPointName.create("com.intellij.directClassInheritorsSearch");
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

    @ApiStatus.Experimental
    public boolean shouldSearchInLanguage(@NotNull Language language) {
      return true;
    }
  }

  private DirectClassInheritorsSearch() {
    super(EP_NAME);
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
    return search(new SearchParameters(aClass, scope, includeAnonymous, true));
  }

  @NotNull
  public static Query<PsiClass> search(@NotNull SearchParameters parameters) {
    return INSTANCE.createUniqueResultsQuery(parameters);
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
