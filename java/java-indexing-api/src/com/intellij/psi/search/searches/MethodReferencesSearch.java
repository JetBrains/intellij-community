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

import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.*;
import com.intellij.util.*;
import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author max
 */
public class MethodReferencesSearch extends ExtensibleQueryFactory<PsiReference, MethodReferencesSearch.SearchParameters> {
  public static final MethodReferencesSearch INSTANCE = new MethodReferencesSearch();

  public static class SearchParameters {
    private final PsiMethod myMethod;
    private final SearchScope myScope;
    private final boolean myStrictSignatureSearch;
    private final SearchRequestCollector myOptimizer;
    private final boolean isSharedOptimizer;

    public SearchParameters(PsiMethod method, SearchScope scope, boolean strictSignatureSearch, @Nullable SearchRequestCollector optimizer) {
      myMethod = method;
      myScope = scope;
      myStrictSignatureSearch = strictSignatureSearch;
      isSharedOptimizer = optimizer != null;
      myOptimizer = optimizer != null ? optimizer : new SearchRequestCollector(new SearchSession());
    }

    public SearchParameters(final PsiMethod aClass, SearchScope scope, final boolean strict) {
      this(aClass, scope, strict, null);
    }

    public PsiMethod getMethod() {
      return myMethod;
    }

    public boolean isStrictSignatureSearch() {
      return myStrictSignatureSearch;
    }

    public SearchRequestCollector getOptimizer() {
      return myOptimizer;
    }

    public SearchScope getScope() {
      return myScope;
    }
  }

  private MethodReferencesSearch() {}

  public static Query<PsiReference> search(final PsiMethod method, SearchScope scope, final boolean strictSignatureSearch) {
    return search(new SearchParameters(method, scope, strictSignatureSearch));
  }

  public static void searchOptimized(final PsiMethod method, SearchScope scope, final boolean strictSignatureSearch,
                                     @NotNull SearchRequestCollector collector, final Processor<PsiReference> processor) {
    searchOptimized(method, scope, strictSignatureSearch, collector, false, new PairProcessor<PsiReference, SearchRequestCollector>() {
      @Override
      public boolean process(PsiReference psiReference, SearchRequestCollector collector) {
        return processor.process(psiReference);
      }
    });
  }
public static void searchOptimized(final PsiMethod method, SearchScope scope, final boolean strictSignatureSearch, SearchRequestCollector collector, final boolean inReadAction, PairProcessor<PsiReference, SearchRequestCollector> processor) {
    final SearchRequestCollector nested = new SearchRequestCollector(collector.getSearchSession());
    collector.searchQuery(new QuerySearchRequest(search(new SearchParameters(method, scope, strictSignatureSearch, nested)), nested,
                                                 inReadAction, processor));
  }

  public static Query<PsiReference> search(final SearchParameters parameters) {
    final Query<PsiReference> result = INSTANCE.createQuery(parameters);
    if (parameters.isSharedOptimizer) {
      return uniqueResults(result);
    }

    final SearchRequestCollector requests = parameters.getOptimizer();

    return uniqueResults(new MergeQuery<PsiReference>(result, new SearchRequestQuery(parameters.getMethod().getProject(), requests)));
  }

  public static Query<PsiReference> search(final PsiMethod method, final boolean strictSignatureSearch) {
    return search(method, GlobalSearchScope.allScope(method.getProject()), strictSignatureSearch);
  }

  public static Query<PsiReference> search(final PsiMethod method) {
    return search(method, true);
  }

  private static UniqueResultsQuery<PsiReference, PsiReference> uniqueResults(Query<PsiReference> composite) {
    //noinspection unchecked
    return new UniqueResultsQuery(composite, TObjectHashingStrategy.CANONICAL, ReferenceDescriptor.MAPPER);
  }

}