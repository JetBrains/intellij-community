// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere;

import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Search process listener interface
 */
public interface SearchListener {
  void elementsAdded(@NotNull List<? extends SearchEverywhereFoundElementInfo> list);

  void elementsRemoved(@NotNull List<? extends SearchEverywhereFoundElementInfo> list);

  void contributorWaits(@NotNull SearchEverywhereContributor<?> contributor);

  void contributorFinished(@NotNull SearchEverywhereContributor<?> contributor, boolean hasMore);

  void searchFinished(@NotNull Map<SearchEverywhereContributor<?>, Boolean> hasMoreContributors);

  void searchStarted(@NotNull Collection<? extends SearchEverywhereContributor<?>> contributors);

  static SearchListener combine(SearchListener... listeners) {
    return new SearchListener() {
      @Override
      public void elementsAdded(@NotNull List<? extends SearchEverywhereFoundElementInfo> list) {
        for (SearchListener l : listeners) l.elementsAdded(list);
      }

      @Override
      public void elementsRemoved(@NotNull List<? extends SearchEverywhereFoundElementInfo> list) {
        for (SearchListener l : listeners) l.elementsRemoved(list);
      }

      @Override
      public void contributorWaits(@NotNull SearchEverywhereContributor<?> contributor) {
        for (SearchListener l : listeners) l.contributorWaits(contributor);
      }

      @Override
      public void contributorFinished(@NotNull SearchEverywhereContributor<?> contributor, boolean hasMore) {
        for (SearchListener l : listeners) l.contributorFinished(contributor, hasMore);
      }

      @Override
      public void searchStarted(@NotNull Collection<? extends SearchEverywhereContributor<?>> contributors) {
        for (SearchListener l : listeners) l.searchStarted(contributors);
      }

      @Override
      public void searchFinished(@NotNull Map<SearchEverywhereContributor<?>, Boolean> hasMoreContributors) {
        for (SearchListener l : listeners) l.searchFinished(hasMoreContributors);
      }
    };
  }
}
