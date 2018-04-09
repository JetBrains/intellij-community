// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.search;

import com.intellij.model.ModelReference;
import com.intellij.model.search.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.TextOccurenceProcessor;
import com.intellij.util.Query;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;

import java.util.*;

import static com.intellij.openapi.util.text.StringUtil.isEmptyOrSpaces;

final class SearchRequestCollectorImpl implements SearchRequestCollector {

  private final Object lock = new Object();

  private final Set<Query<? extends ModelReference>> mySubQueries = new LinkedHashSet<>();
  private final Set<ModelReferenceSearchParameters> mySubSearchParameters = new LinkedHashSet<>();
  /**
   * Requests that does not require Processor.<br/>
   * There requests' {@link TextOccurenceProcessor processors} may add another requests while being processed.
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

  SearchRequestCollectorImpl(@NotNull Query<ModelReference> rootQuery) {
    searchSubQuery(rootQuery);
  }

  @Override
  public void searchSubQuery(@NotNull Query<? extends ModelReference> subQuery) {
    synchronized (lock) {
      if (subQuery instanceof ModelReferenceSearchQuery) {
        // unwrap subQuery into current session
        ModelReferenceSearchQuery referenceSearchQuery = (ModelReferenceSearchQuery)subQuery;
        mySubQueries.add(referenceSearchQuery.getBaseQuery());
        mySubSearchParameters.add(referenceSearchQuery.getParameters());
      }
      else {
        mySubQueries.add(subQuery);
      }
    }
  }

  @NotNull
  @Override
  public SearchWordRequestor searchWord(@NotNull String word, @NotNull SearchScope searchScope) {
    if (isEmptyOrSpaces(word)) {
      throw new IllegalArgumentException("Cannot search for elements with empty text");
    }
    return makesSenseToSearch(searchScope) ? new SearchWordRequestorImpl(this, word, searchScope)
                                           : EmptySearchWordRequestor.INSTANCE;
  }

  void searchWord(@NotNull Collection<SearchWordRequest> requests, @NotNull TextOccurenceProcessor processor) {
    synchronized (lock) {
      for (SearchWordRequest request : requests) {
        myImmediateWordRequests.computeIfAbsent(request, r -> new SmartList<>()).add(processor);
      }
    }
  }

  void searchWord(@NotNull Collection<SearchWordRequest> requests, @NotNull TextOccurenceProcessorProvider f) {
    synchronized (lock) {
      for (SearchWordRequest request : requests) {
        myDeferredWordRequests.computeIfAbsent(request, r -> new SmartList<>()).add(f);
      }
    }
  }

  boolean hasMoreRequests() {
    synchronized (lock) {
      return !mySubQueries.isEmpty() ||
             !mySubSearchParameters.isEmpty() ||
             !myImmediateWordRequests.isEmpty() ||
             !myDeferredWordRequests.isEmpty();
    }
  }

  @NotNull
  Collection<Query<? extends ModelReference>> takeSubQueries() {
    synchronized (lock) {
      return take(mySubQueries);
    }
  }

  @NotNull
  Collection<ModelReferenceSearchParameters> takeSubSearchParameters() {
    synchronized (lock) {
      return take(mySubSearchParameters);
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

  private static boolean makesSenseToSearch(@NotNull SearchScope searchScope) {
    if (searchScope instanceof LocalSearchScope && ((LocalSearchScope)searchScope).getScope().length == 0) {
      return false;
    }
    else {
      return searchScope != GlobalSearchScope.EMPTY_SCOPE;
    }
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