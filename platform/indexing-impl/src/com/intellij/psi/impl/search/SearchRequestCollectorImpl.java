// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.search;

import com.intellij.model.Symbol;
import com.intellij.model.SymbolReference;
import com.intellij.model.search.*;
import com.intellij.psi.search.TextOccurenceProcessor;
import com.intellij.util.Preprocessor;
import com.intellij.util.Query;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;

import java.util.*;

import static com.intellij.openapi.util.text.StringUtil.isEmptyOrSpaces;

final class SearchRequestCollectorImpl implements SearchRequestCollector {

  private final Object lock = new Object();

  private final Set<SearchQueryRequest<?>> myQueryRequests = new LinkedHashSet<>();
  private final Set<SearchParamsRequest> myParamsRequests = new LinkedHashSet<>();
  /**
   * Requests that does not require Processor.<br/>
   * These requests' {@link TextOccurenceProcessor processors} may add other requests while being processed.
   * <p>
   * {@code SearchWordRequest -> TextOccurenceProcessor}
   */
  private final Map<SearchWordRequest, List<TextOccurenceProcessor>> myImmediateWordRequests = new LinkedHashMap<>();
  /**
   * Requests that require Processor to feed elements into it.<br/>
   * These requests should not add any another requests.
   * <p>
   * {@code SearchWordRequest -> [Processor -> TextOccurenceProcessor]}
   */
  private final Map<SearchWordRequest, List<TextOccurenceProcessorProvider>> myDeferredWordRequests = new LinkedHashMap<>();

  private final SymbolReferenceSearchParameters myParameters;
  private final Preprocessor<SymbolReference, SymbolReference> myPreprocessor;

  SearchRequestCollectorImpl(@NotNull SymbolReferenceSearchParameters parameters,
                             @NotNull Preprocessor<SymbolReference, SymbolReference> preprocessor) {
    myParameters = parameters;
    myPreprocessor = preprocessor;
  }

  @NotNull
  @Override
  public SymbolReferenceSearchParameters getParameters() {
    return myParameters;
  }

  @Override
  public void searchSubQuery(@NotNull Query<? extends SymbolReference> subQuery) {
    searchSubQuery(subQuery, Preprocessor.id());
  }

  @Override
  public <T> void searchSubQuery(@NotNull Query<T> subQuery, @NotNull Preprocessor<SymbolReference, T> preprocessor) {
    synchronized (lock) {
      if (subQuery instanceof SymbolReferenceSearchQuery) {
        // unwrap subQuery into current session
        SymbolReferenceSearchQuery referenceSearchQuery = (SymbolReferenceSearchQuery)subQuery;
        // T is SymbolReference, but java can't infer that
        //noinspection unchecked
        Preprocessor<SymbolReference, SymbolReference> referencePreprocessor = (Preprocessor<SymbolReference, SymbolReference>)preprocessor;
        searchSubQuery(referenceSearchQuery.getBaseQuery(), referencePreprocessor);
        searchParams(referenceSearchQuery.getParameters(), referencePreprocessor);
      }
      else {
        myQueryRequests.add(new SearchQueryRequest<>(subQuery, Preprocessor.andThen(myPreprocessor, preprocessor)));
      }
    }
  }

  @NotNull
  @Override
  public SearchTargetRequestor searchTarget(@NotNull Symbol target) {
    return new SearchTargetRequestorImpl(this, target);
  }

  void searchParams(@NotNull SymbolReferenceSearchParameters parameters,
                    @NotNull Preprocessor<SymbolReference, SymbolReference> preprocessor) {
    synchronized (lock) {
      myParamsRequests.add(new SearchParamsRequest(parameters, Preprocessor.andThen(myPreprocessor, preprocessor)));
    }
  }

  @NotNull
  @Override
  public SearchWordRequestor searchWord(@NotNull String word) {
    if (isEmptyOrSpaces(word)) {
      throw new IllegalArgumentException("Cannot search for elements with empty text");
    }
    return new SearchWordRequestorImpl(this, word);
  }

  void searchWord(@NotNull Collection<SearchWordRequest> requests, @NotNull TextOccurenceProcessor processor) {
    synchronized (lock) {
      for (SearchWordRequest request : requests) {
        myImmediateWordRequests.computeIfAbsent(request, r -> new SmartList<>()).add(processor);
      }
    }
  }

  void searchWord(@NotNull Collection<SearchWordRequest> requests, @NotNull TextOccurenceProcessorProvider f) {
    if (requests.isEmpty()) return;
    synchronized (lock) {
      for (SearchWordRequest request : requests) {
        myDeferredWordRequests.computeIfAbsent(request, r -> new SmartList<>()).add(processor -> f.apply(myPreprocessor.apply(processor)));
      }
    }
  }

  boolean isEmpty() {
    synchronized (lock) {
      return myQueryRequests.isEmpty() &&
             myParamsRequests.isEmpty() &&
             myImmediateWordRequests.isEmpty() &&
             myDeferredWordRequests.isEmpty();
    }
  }

  @NotNull
  Collection<SearchQueryRequest<?>> takeQueryRequests() {
    synchronized (lock) {
      return take(myQueryRequests);
    }
  }

  @NotNull
  Collection<SearchParamsRequest> takeParametersRequests() {
    synchronized (lock) {
      return take(myParamsRequests);
    }
  }

  @NotNull
  WordRequests takeWordRequests() {
    synchronized (lock) {
      return new WordRequests(take(myImmediateWordRequests), take(myDeferredWordRequests));
    }
  }

  private static <T> Set<T> take(Set<T> set) {
    LinkedHashSet<T> result = new LinkedHashSet<>(set);
    set.clear();
    return result;
  }

  private static <K, V> Map<K, V> take(Map<K, V> map) {
    Map<K, V> result = new LinkedHashMap<>(map);
    map.clear();
    return result;
  }
}

final class WordRequests {

  final Map<SearchWordRequest, List<TextOccurenceProcessor>> immediateWordRequests;
  final Map<SearchWordRequest, List<TextOccurenceProcessorProvider>> deferredWordRequests;

  WordRequests(Map<SearchWordRequest, List<TextOccurenceProcessor>> immediateRequests,
               Map<SearchWordRequest, List<TextOccurenceProcessorProvider>> deferredRequests) {
    immediateWordRequests = immediateRequests;
    deferredWordRequests = deferredRequests;
  }

  boolean isEmpty() {
    return immediateWordRequests.isEmpty() && deferredWordRequests.isEmpty();
  }
}