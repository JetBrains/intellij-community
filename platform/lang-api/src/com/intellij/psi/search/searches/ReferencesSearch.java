/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.*;
import com.intellij.util.*;
import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author max
 */
public class ReferencesSearch extends ExtensibleQueryFactory<PsiReference, ReferencesSearch.SearchParameters> {
  private static final ReferencesSearch INSTANCE = new ReferencesSearch();

  private ReferencesSearch() {
  }

  public static class SearchParameters {
    private final PsiElement myElementToSearch;
    private final SearchScope myScope;
    private final boolean myIgnoreAccessScope;
    private final SearchRequestCollector myOptimizer;
    private final boolean isSharedOptimizer;

    public SearchParameters(@NotNull PsiElement elementToSearch, SearchScope scope, boolean ignoreAccessScope, @Nullable SearchRequestCollector optimizer) {
      myElementToSearch = elementToSearch;
      myScope = scope;
      myIgnoreAccessScope = ignoreAccessScope;
      isSharedOptimizer = optimizer != null;
      myOptimizer = optimizer == null ? new SearchRequestCollector(new SearchSession()) : optimizer;
    }

    public SearchParameters(@NotNull final PsiElement elementToSearch, final SearchScope scope, final boolean ignoreAccessScope) {
      this(elementToSearch, scope, ignoreAccessScope, null);
    }

    public PsiElement getElementToSearch() {
      return myElementToSearch;
    }

    public SearchScope getScope() {
      return myScope;
    }

    public boolean isIgnoreAccessScope() {
      return myIgnoreAccessScope;
    }

    public SearchRequestCollector getOptimizer() {
      return myOptimizer;
    }

    public SearchScope getEffectiveSearchScope () {
      if (!myIgnoreAccessScope) {
        SearchScope accessScope = myElementToSearch.getManager().getSearchHelper().getUseScope(myElementToSearch);
        return myScope.intersectWith(accessScope);
      }
      else {
        return myScope;
      }
    }
  }

  public static Query<PsiReference> search(@NotNull PsiElement element) {
    return search(element, GlobalSearchScope.projectScope(element.getProject()), false);
  }

  public static Query<PsiReference> search(@NotNull PsiElement element, @NotNull SearchScope searchScope) {
    return search(element, searchScope, false);
  }

  public static Query<PsiReference> search(@NotNull PsiElement element, @NotNull SearchScope searchScope, boolean ignoreAccessScope) {
    return search(new SearchParameters(element, searchScope, ignoreAccessScope));
  }

  public static Query<PsiReference> search(@NotNull final SearchParameters parameters) {
    final Query<PsiReference> result = INSTANCE.createQuery(parameters);
    if (parameters.isSharedOptimizer) {
      return uniqueResults(result);
    }

    final SearchRequestCollector requests = parameters.getOptimizer();

    final PsiElement element = parameters.getElementToSearch();

    return uniqueResults(new MergeQuery<PsiReference>(result, new SearchRequestQuery(element.getProject(), requests)));
  }

  private static UniqueResultsQuery<PsiReference, PsiReference> uniqueResults(Query<PsiReference> composite) {
    //noinspection unchecked
    return new UniqueResultsQuery(composite, TObjectHashingStrategy.CANONICAL, ReferenceDescriptor.MAPPER);
  }

  public static void searchOptimized(@NotNull PsiElement element, @NotNull SearchScope searchScope, boolean ignoreAccessScope,
                                     @NotNull SearchRequestCollector collector, final Processor<PsiReference> processor) {
    searchOptimized(element, searchScope, ignoreAccessScope, collector, false, new PairProcessor<PsiReference, SearchRequestCollector>() {
      @Override
      public boolean process(PsiReference psiReference, SearchRequestCollector collector) {
        return processor.process(psiReference);
      }
    });
  }
  public static void searchOptimized(@NotNull PsiElement element, @NotNull SearchScope searchScope, boolean ignoreAccessScope,
                                     @NotNull SearchRequestCollector collector, final boolean inReadAction, PairProcessor<PsiReference, SearchRequestCollector> processor) {
    final SearchRequestCollector nested = new SearchRequestCollector(collector.getSearchSession());
    collector.searchQuery(new QuerySearchRequest(search(new SearchParameters(element, searchScope, ignoreAccessScope, nested)), nested,
                                                 inReadAction, processor));
  }


}
