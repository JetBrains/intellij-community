// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import org.jetbrains.annotations.Nullable;

/**
 * Search for <em>direct</em> inheritors of given class.
 * <p/>
 * For given hierarchy
 * <pre>
 *   class A {}
 *   class B extends A {}
 *   class C extends B {}
 * </pre>
 * searching for inheritors of {@code A} returns {@code B}.
 * <p/>
 * See {@link ClassInheritorsSearch} to search for all inheritors.
 *
 * @see com.intellij.psi.util.InheritanceUtil
 */
public final class DirectClassInheritorsSearch extends ExtensibleQueryFactory<PsiClass, DirectClassInheritorsSearch.SearchParameters> {
  public static final ExtensionPointName<QueryExecutor<PsiClass, DirectClassInheritorsSearch.SearchParameters>> EP_NAME = ExtensionPointName.create("com.intellij.directClassInheritorsSearch");
  public static final DirectClassInheritorsSearch INSTANCE = new DirectClassInheritorsSearch();

  public static class SearchParameters {
    private final @NotNull PsiClass myClass;
    private final @NotNull SearchScope myScope;
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

    public @NotNull PsiClass getClassToProcess() {
      return myClass;
    }

    public @NotNull SearchScope getScope() {
      return myScope;
    }

    public boolean isCheckInheritance() {
      return myCheckInheritance;
    }

    public boolean includeAnonymous() {
      return myIncludeAnonymous;
    }

    public @Nullable ClassInheritorsSearch.SearchParameters getOriginalParameters() {
      return null;
    }

    @ApiStatus.Experimental
    public boolean shouldSearchInLanguage(@NotNull Language language) {
      return true;
    }
  }

  private DirectClassInheritorsSearch() {
    super(EP_NAME);
  }

  public static @NotNull Query<PsiClass> search(@NotNull PsiClass aClass) {
    return search(aClass, GlobalSearchScope.allScope(PsiUtilCore.getProjectInReadAction(aClass)));
  }

  public static @NotNull Query<PsiClass> search(@NotNull PsiClass aClass, @NotNull SearchScope scope) {
    return search(aClass, scope, true);
  }

  public static @NotNull Query<PsiClass> search(@NotNull PsiClass aClass, @NotNull SearchScope scope, boolean includeAnonymous) {
    return search(new SearchParameters(aClass, scope, includeAnonymous, true));
  }

  public static @NotNull Query<PsiClass> search(@NotNull SearchParameters parameters) {
    return INSTANCE.createUniqueResultsQuery(parameters);
  }
}
