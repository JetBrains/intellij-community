// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.search.searches;

import com.intellij.openapi.application.DumbAwareSearchParameters;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.*;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class MethodReferencesSearch extends ExtensibleQueryFactory<PsiReference, MethodReferencesSearch.SearchParameters> {
  public static final ExtensionPointName<QueryExecutor<PsiReference, MethodReferencesSearch.SearchParameters>> EP_NAME = new ExtensionPointName<>("com.intellij.methodReferencesSearch");
  public static final MethodReferencesSearch INSTANCE = new MethodReferencesSearch();

  public static class SearchParameters implements DumbAwareSearchParameters {
    private final PsiMethod myMethod;
    private final Project myProject;
    private final SearchScope myScope;
    private volatile SearchScope myEffectiveScope;
    private final boolean myStrictSignatureSearch;
    private final SearchRequestCollector myOptimizer;
    private final boolean isSharedOptimizer;

    public SearchParameters(@NotNull PsiMethod method, @NotNull SearchScope scope, boolean strictSignatureSearch, @Nullable SearchRequestCollector optimizer) {
      myMethod = method;
      myScope = scope;
      myStrictSignatureSearch = strictSignatureSearch;
      isSharedOptimizer = optimizer != null;
      myOptimizer = optimizer != null ? optimizer : new SearchRequestCollector(new SearchSession(method));
      myProject = PsiUtilCore.getProjectInReadAction(method);
    }

    public SearchParameters(@NotNull PsiMethod method, @NotNull SearchScope scope, final boolean strict) {
      this(method, scope, strict, null);
    }

    @Override
    public boolean isQueryValid() {
      return myMethod.isValid();
    }

    @Override
    @NotNull
    public Project getProject() {
      return myProject;
    }

    @NotNull
    public PsiMethod getMethod() {
      return myMethod;
    }

    public boolean isStrictSignatureSearch() {
      return myStrictSignatureSearch;
    }

    public SearchRequestCollector getOptimizer() {
      return myOptimizer;
    }

    /**
     * @return the user-visible search scope, most often "Project Files" or "Project and Libraries".
     * Searchers most likely need to use {@link #getEffectiveSearchScope()}.
     */
    public SearchScope getScopeDeterminedByUser() {
      return myScope;
    }


    /**
     * @deprecated Same as {@link #getScopeDeterminedByUser()}. Searchers most likely need to use {@link #getEffectiveSearchScope()}.
     */
    @Deprecated(forRemoval = true)
    @NotNull
    public SearchScope getScope() {
      return getScopeDeterminedByUser();
    }

    @NotNull
    public SearchScope getEffectiveSearchScope () {
      SearchScope scope = myEffectiveScope;
      if (scope == null) {
        if (!myMethod.isValid()) return LocalSearchScope.EMPTY;

        myEffectiveScope = scope = myScope.intersectWith(PsiSearchHelper.getInstance(myMethod.getProject()).getUseScope(myMethod));
      }
      return scope;
    }
  }

  private MethodReferencesSearch() {
    super(EP_NAME);
  }

  public static @NotNull Query<PsiReference> search(@NotNull PsiMethod method, @NotNull SearchScope scope, final boolean strictSignatureSearch) {
    return search(new SearchParameters(method, scope, strictSignatureSearch));
  }

  public static void searchOptimized(@NotNull PsiMethod method,
                                     @NotNull SearchScope scope,
                                     boolean strictSignatureSearch,
                                     @NotNull SearchRequestCollector collector,
                                     @NotNull Processor<? super PsiReference> processor) {
    searchOptimized(method, scope, strictSignatureSearch, collector, false, (psiReference, collector1) -> processor.process(psiReference));
  }

  public static void searchOptimized(@NotNull PsiMethod method,
                                     @NotNull SearchScope scope,
                                     boolean strictSignatureSearch,
                                     @NotNull SearchRequestCollector collector,
                                     boolean inReadAction,
                                     @NotNull PairProcessor<? super PsiReference, ? super SearchRequestCollector> processor) {
    final SearchRequestCollector nested = new SearchRequestCollector(collector.getSearchSession());
    collector.searchQuery(new QuerySearchRequest(search(new SearchParameters(method, scope, strictSignatureSearch, nested)), nested,
                                                 inReadAction, processor));
  }

  public static @NotNull Query<PsiReference> search(@NotNull SearchParameters parameters) {
    final Query<PsiReference> result = INSTANCE.createQuery(parameters);
    if (parameters.isSharedOptimizer) {
      return uniqueResults(result);
    }

    final SearchRequestCollector requests = parameters.getOptimizer();

    Project project = PsiUtilCore.getProjectInReadAction(parameters.getMethod());
    return uniqueResults(new MergeQuery<>(result, new SearchRequestQuery(project, requests)));
  }

  public static @NotNull Query<PsiReference> search(@NotNull PsiMethod method, final boolean strictSignatureSearch) {
    return search(method, GlobalSearchScope.allScope(PsiUtilCore.getProjectInReadAction(method)), strictSignatureSearch);
  }

  public static @NotNull Query<PsiReference> search(@NotNull PsiMethod method) {
    return search(method, true);
  }

  private static @NotNull Query<PsiReference> uniqueResults(@NotNull Query<? extends PsiReference> composite) {
    return new UniqueResultsQuery<>(composite, ReferenceDescriptor.MAPPER);
  }
}