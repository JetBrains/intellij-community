// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.searcheverywhere;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.util.Pair;
import com.intellij.util.ConcurrencyUtil;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Collectors;

/**
 * Single thread implementation of {@link SESearcher}.
 * Being used only as a temporary solution in case of problems with {@link MultithreadSearcher}.
 */
@Deprecated
class SingleThreadSearcher implements SESearcher {

  private final Executor myNotificationExecutor;
  private final Listener myNotificationListener;

  SingleThreadSearcher(Listener listener, Executor executor) {
    myNotificationExecutor = executor;
    myNotificationListener = listener;
  }

  @Override
  public ProgressIndicator search(Map<SearchEverywhereContributor<?>, Integer> contributorsAndLimits, String pattern, boolean useNonProjectItems,
                                  Function<SearchEverywhereContributor<?>, SearchEverywhereContributorFilter<?>> filterSupplier) {
    ProgressIndicator indicator = new ProgressIndicatorBase();
    Runnable task = new SearchTask(contributorsAndLimits, pattern, useNonProjectItems, filterSupplier, indicator, myNotificationExecutor, myNotificationListener);
    ApplicationManager.getApplication().executeOnPooledThread(ConcurrencyUtil.underThreadNameRunnable("SE-SingleThread-SearchTask", task));

    return indicator;
  }

  @Override
  public ProgressIndicator findMoreItems(Map<SearchEverywhereContributor<?>, Collection<ElementInfo>> alreadyFound, String pattern,
                                         boolean useNonProjectItems, SearchEverywhereContributor<?> contributorToExpand, int newLimit,
                                         Function<SearchEverywhereContributor<?>, SearchEverywhereContributorFilter<?>> filterSupplier) {
    ProgressIndicator indicator = new ProgressIndicatorBase();
    Runnable task = createShowMoreTask(contributorToExpand, newLimit, pattern, useNonProjectItems, alreadyFound, filterSupplier, indicator);
    ApplicationManager.getApplication().executeOnPooledThread(ConcurrencyUtil.underThreadNameRunnable("SE-SingleThread-SearchTask", task));

    return indicator;
  }

  @NotNull
  private <F> ShowMoreTask<F> createShowMoreTask(SearchEverywhereContributor<F> contributorToExpand,
                                                 int newLimit,
                                                 String pattern,
                                                 boolean useNonProjectItems,
                                                 Map<SearchEverywhereContributor<?>, Collection<ElementInfo>> alreadyFound,
                                                 Function<SearchEverywhereContributor<?>, SearchEverywhereContributorFilter<?>> filterSupplier,
                                                 ProgressIndicator indicator) {
    List<ElementInfo> alreadyFoundList = alreadyFound.values()
      .stream()
      .collect(Collector.of(() -> new ArrayList<>(), (list, infos) -> list.addAll(infos), (left, right) -> {
        left.addAll(right);
        return left;
      }));
    return new ShowMoreTask<>(contributorToExpand, newLimit, pattern, useNonProjectItems,
                              (SearchEverywhereContributorFilter<F>) filterSupplier.apply(contributorToExpand), alreadyFoundList, indicator,
                              myNotificationExecutor, myNotificationListener);
  }

  //Pair<elementsToAdd, elementsToRemove>
  private static <T> Pair<List<ElementInfo>, List<ElementInfo>> calculateUpdates(SearchEverywhereContributor<?> contributor, String pattern, List<T> searchResults, Map<T, ElementInfo> alreadyFound) {
    List<ElementInfo> toAdd = new ArrayList<>();
    List<ElementInfo> toRemove = new ArrayList<>();
    searchResults.forEach(newElement -> {
      int priority = contributor.getElementPriority(newElement, pattern);
      ElementInfo found = alreadyFound.get(newElement);
      if (found != null) {
        if (found.priority < priority) {
          toRemove.add(found);
          alreadyFound.remove(newElement);
        } else {
          return;
        }
      }
      ElementInfo newInfo = new ElementInfo(newElement, priority, contributor);
      toAdd.add(newInfo);
      alreadyFound.put(newElement, newInfo);
    });

    return Pair.create(toAdd, toRemove);
  }

  private static class SearchTask implements Runnable {
    private final Map<SearchEverywhereContributor<?>, Integer> myContributorsAndLimits;
    private final String myPattern;
    private final boolean myUseNonProjectItems;
    private final Function<SearchEverywhereContributor<?>, SearchEverywhereContributorFilter<?>> myFilterSupplier;

    private final ProgressIndicator myProgressIndicator;
    private final Executor notificationExecutor;
    private final Listener notificationListener;

    SearchTask(Map<SearchEverywhereContributor<?>, Integer> contributorsAndLimits, String pattern, boolean useNonProjectItems,
                      Function<SearchEverywhereContributor<?>, SearchEverywhereContributorFilter<?>> filterSupplier,
                      ProgressIndicator progressIndicator, Executor notificationExecutor, Listener notificationListener) {
      myContributorsAndLimits = contributorsAndLimits;
      myPattern = pattern;
      myUseNonProjectItems = useNonProjectItems;
      myFilterSupplier = filterSupplier;
      myProgressIndicator = progressIndicator;
      this.notificationExecutor = notificationExecutor;
      this.notificationListener = notificationListener;
    }

    @Override
    public void run() {
      Map<SearchEverywhereContributor<?>, Boolean> hasMoreContributors = new HashMap<>();
      Map<Object, ElementInfo> alreadyFound = new HashMap<>();

      myContributorsAndLimits.entrySet()
        .stream()
        .sorted(Comparator.comparingInt(entry -> entry.getKey().getSortWeight()))
        .forEach(entry -> {
          SearchEverywhereContributor<?> contributor = entry.getKey();
          int limit = entry.getValue();
          ContributorSearchResult<Object> results = search(contributor, limit);
          Pair<List<ElementInfo>, List<ElementInfo>> updates = calculateUpdates(contributor, myPattern, results.getItems(), alreadyFound);
          notificationExecutor.execute(() -> notificationListener.elementsAdded(updates.first));
          notificationExecutor.execute(() -> notificationListener.elementsRemoved(updates.second));
          hasMoreContributors.put(contributor, results.hasMoreItems());
        });

      notificationExecutor.execute(() -> notificationListener.searchFinished(hasMoreContributors));
    }

    private <F> ContributorSearchResult<Object> search(SearchEverywhereContributor<F> contributor, Integer limit) {
      SearchEverywhereContributorFilter<F> filter = (SearchEverywhereContributorFilter<F>) myFilterSupplier.apply(contributor);
      return contributor.search(myPattern, myUseNonProjectItems, filter, myProgressIndicator, limit);
    }
  }

  private static class ShowMoreTask<F> implements Runnable {
    private final SearchEverywhereContributor<F> myContributor;
    private final int myLimit;
    private final String myPattern;
    private final boolean myUseNonProjectItems;
    private final SearchEverywhereContributorFilter<F> myFilter;
    private final List<ElementInfo> myAlreadyFound;

    private final ProgressIndicator myProgressIndicator;
    private final Executor notificationExecutor;
    private final Listener notificationListener;

    private ShowMoreTask(SearchEverywhereContributor<F> contributor, int limit, String pattern, boolean useNonProjectItems,
                         SearchEverywhereContributorFilter<F> filter, List<ElementInfo> alreadyFound, ProgressIndicator indicator,
                         Executor executor, Listener listener) {
      myContributor = contributor;
      myLimit = limit;
      myPattern = pattern;
      myUseNonProjectItems = useNonProjectItems;
      myFilter = filter;
      myAlreadyFound = alreadyFound;
      myProgressIndicator = indicator;
      notificationExecutor = executor;
      notificationListener = listener;
    }

    @Override
    public void run() {
      Map<Object, ElementInfo> alreadyFoundMap = myAlreadyFound.stream().collect(Collectors.toMap(info -> info.element, Function.identity()));
      ContributorSearchResult<Object> results = myContributor.search(myPattern, myUseNonProjectItems, myFilter, myProgressIndicator, myLimit);
      Pair<List<ElementInfo>, List<ElementInfo>> updates = calculateUpdates(myContributor, myPattern, results.getItems(), alreadyFoundMap);
      notificationExecutor.execute(() -> notificationListener.elementsAdded(updates.first));
      notificationExecutor.execute(() -> notificationListener.elementsRemoved(updates.second));
      notificationExecutor.execute(() -> notificationListener.searchFinished(Collections.singletonMap(myContributor, results.hasMoreItems())));
    }
  }

}
