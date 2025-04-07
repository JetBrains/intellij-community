// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.search.searches;

import com.intellij.lang.Language;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
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
    private final boolean myRestrictSealedHierarchy;

    /**
     * @param aClass class to search inheritors of
     * @param scope scope to search in
     * @param includeAnonymous whether to include anonymous inheritors of the class
     * @param restrictSealedHierarchy if false, inheritors of sealed class undeclared in permits list will be found as well
     */
    public SearchParameters(@NotNull PsiClass aClass, @NotNull SearchScope scope, boolean includeAnonymous, boolean checkInheritance,
                            boolean restrictSealedHierarchy) {
      myClass = aClass;
      myScope = scope;
      myIncludeAnonymous = includeAnonymous;
      myCheckInheritance = checkInheritance;
      myRestrictSealedHierarchy = restrictSealedHierarchy;
    }

    public SearchParameters(@NotNull PsiClass aClass, @NotNull SearchScope scope, boolean includeAnonymous, boolean checkInheritance) {
      this(aClass, scope, includeAnonymous, checkInheritance, true);
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

    /**
     * @return false if inheritors of sealed class undeclared in the 'permits' list should be found as well.
     */
    public boolean restrictSealedHierarchy() {
      return myRestrictSealedHierarchy;
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
    GlobalSearchScope scope = GlobalSearchScope.allScope(PsiUtilCore.getProjectInReadAction(aClass));
    PsiFile file = aClass.getContainingFile();
    if (file != null) {
      VirtualFile vFile = file.getVirtualFile();
      if (vFile != null && !scope.contains(vFile)) {
        // include file scope to properly support scratch files
        scope = scope.union(GlobalSearchScope.fileScope(file));
      }
    }
    return search(aClass, scope);
  }

  public static @NotNull Query<PsiClass> search(@NotNull PsiClass aClass, @NotNull SearchScope scope) {
    return search(aClass, scope, true);
  }

  public static @NotNull Query<PsiClass> search(@NotNull PsiClass aClass, @NotNull SearchScope scope, boolean includeAnonymous) {
    return search(new SearchParameters(aClass, scope, includeAnonymous, true));
  }

  /**
   * @param aClass class to search inheritors of
   * @param scope scope to search in
   * @return query that returns all inheritors of the given class, including not permitted inheritors if (aClass is sealed)
   */
  public static @NotNull Query<PsiClass> searchAllSealedInheritors(@NotNull PsiClass aClass, @NotNull SearchScope scope) {
    return search(new SearchParameters(aClass, scope, true, true, false));
  }

  public static @NotNull Query<PsiClass> search(@NotNull SearchParameters parameters) {
    return INSTANCE.createUniqueResultsQuery(parameters);
  }
}
