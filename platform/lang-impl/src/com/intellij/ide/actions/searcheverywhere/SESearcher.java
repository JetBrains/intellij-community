// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.searcheverywhere;

import com.intellij.openapi.progress.ProgressIndicator;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

interface SESearcher {
  ProgressIndicator search(Map<SearchEverywhereContributor<?>, Integer> contributorsAndLimits, String pattern,
                           boolean useNonProjectItems,
                           Function<SearchEverywhereContributor<?>, SearchEverywhereContributorFilter<?>> filterSupplier);

  ProgressIndicator findMoreItems(Map<SearchEverywhereContributor<?>, Collection<ElementInfo>> alreadyFound, String pattern,
                                  boolean useNonProjectItems, SearchEverywhereContributor<?> contributorToExpand, int newLimit,
                                  Function<SearchEverywhereContributor<?>, SearchEverywhereContributorFilter<?>> filterSupplier);

  /**
   * Search process listener interface
   */
  interface Listener {
    void elementsAdded(@NotNull List<ElementInfo> list);
    void elementsRemoved(@NotNull List<ElementInfo> list);
    void searchFinished(@NotNull Map<SearchEverywhereContributor<?>, Boolean> hasMoreContributors);
  }

  /**
   * Class containing info about found elements
   */
  class ElementInfo {
    public final int priority;
    public final Object element;
    public final SearchEverywhereContributor<?> contributor;

    public ElementInfo(Object element, int priority, SearchEverywhereContributor<?> contributor) {
      this.priority = priority;
      this.element = element;
      this.contributor = contributor;
    }

    public int getPriority() {
      return priority;
    }

    public Object getElement() {
      return element;
    }

    public SearchEverywhereContributor<?> getContributor() {
      return contributor;
    }
  }
}
