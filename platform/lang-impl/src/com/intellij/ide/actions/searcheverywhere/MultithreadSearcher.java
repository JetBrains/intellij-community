// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.searcheverywhere;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.util.Pair;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Phaser;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.stream.Collectors;

class MultithreadSearcher {

  private static final Logger LOG = Logger.getInstance(MultithreadSearcher.class);

  private final Listener myListener;
  private final Executor myNotificationExecutor;

  public MultithreadSearcher(@NotNull Listener listener, Executor notificationExecutor) {
    myListener = listener;
    myNotificationExecutor = notificationExecutor;
  }

  public MultithreadSearcher(@NotNull Listener listener) {
    this(listener, Runnable::run);
  }

  public ProgressIndicator search(Map<SearchEverywhereContributor<?>, Integer> contributorsAndLimits, String pattern,
                                  boolean useNonProjectItems,
                                  Function<SearchEverywhereContributor<?>, SearchEverywhereContributorFilter<?>> filterSupplier) {
    LOG.debug("Search started for pattern [", pattern, "]");
    Phaser phaser = new Phaser();
    FullSearchResultsAccumulator accumulator = new FullSearchResultsAccumulator(contributorsAndLimits, myListener, myNotificationExecutor);
    ProgressIndicator indicator = new ProgressIndicatorBase() {
      @Override
      protected void onRunningChange() {
        if (isCanceled()) {
          accumulator.stop();
        }
      }
    };
    indicator.start();

    Runnable finisherTask = createFinisherTask(phaser, accumulator);
    for (SearchEverywhereContributor<?> contributor : contributorsAndLimits.keySet()) {
      SearchEverywhereContributorFilter<?> filter = filterSupplier.apply(contributor);
      phaser.register();
      Runnable task = createSearchTask(pattern, useNonProjectItems, accumulator, indicator, contributor, filter, () -> phaser.arrive());
      ApplicationManager.getApplication().executeOnPooledThread(task);
    }
    ApplicationManager.getApplication().executeOnPooledThread(finisherTask);

    return indicator;
  }

  public ProgressIndicator findMoreItems(Map<SearchEverywhereContributor<?>, Collection<ElementInfo>> alreadyFound, String pattern,
                                         boolean useNonProjectItems, SearchEverywhereContributor<?> contributorToExpand, int newLimit,
                                         Function<SearchEverywhereContributor<?>, SearchEverywhereContributorFilter<?>> filterSupplier) {
    ResultsAccumulator accumulator = new ShowMoreResultsAccumulator(alreadyFound, contributorToExpand, newLimit, myListener, myNotificationExecutor);
    SearchEverywhereContributorFilter<?> filter = filterSupplier.apply(contributorToExpand);
    ProgressIndicator indicator = new ProgressIndicatorBase();
    indicator.start();
    Runnable task = createSearchTask(pattern, useNonProjectItems, accumulator, indicator, contributorToExpand, filter, () -> indicator.stop());
    ApplicationManager.getApplication().executeOnPooledThread(task);

    return indicator;
  }

  @NotNull
  private static <F> Runnable createSearchTask(String pattern, boolean useNonProjectItems, ResultsAccumulator accumulator,
                                               ProgressIndicator indicator, SearchEverywhereContributor<F> contributor,
                                               SearchEverywhereContributorFilter<?> filter, Runnable finalCallback) {
    ContributorSearchTask<F> task = new ContributorSearchTask<>(contributor, pattern, (SearchEverywhereContributorFilter<F>)filter,
                                                                useNonProjectItems, accumulator, indicator, finalCallback);
    return ConcurrencyUtil.underThreadNameRunnable("SE-SearchTask", task);
  }

  private static Runnable createFinisherTask(Phaser phaser, FullSearchResultsAccumulator accumulator) {
    phaser.register();

    return ConcurrencyUtil.underThreadNameRunnable("SE-FinisherTask", () -> {
      phaser.arriveAndAwaitAdvance();
      accumulator.searchFinished();
    });
  }

  public interface Listener {
    void elementsAdded(List<ElementInfo> list);
    void elementsRemoved(List<ElementInfo> list);
    void searchFinished(Map<SearchEverywhereContributor<?>, Boolean> hasMoreContributors);
  }

  private static class ContributorSearchTask<F> implements Runnable {

    private final ResultsAccumulator myAccumulator;
    private final Runnable finishCallback;

    private final SearchEverywhereContributor<F> myContributor;
    private final SearchEverywhereContributorFilter<F> filter;
    private final String myPattern;
    private final boolean myUseNonProjectItems;
    private final ProgressIndicator myIndicator;

    private ContributorSearchTask(SearchEverywhereContributor<F> contributor,
                                  String pattern,
                                  SearchEverywhereContributorFilter<F> filter,
                                  boolean everywhere,
                                  ResultsAccumulator accumulator, ProgressIndicator indicator, Runnable callback) {
      myContributor = contributor;
      myPattern = pattern;
      this.filter = filter;
      myUseNonProjectItems = everywhere;
      myAccumulator = accumulator;
      myIndicator = indicator;
      finishCallback = callback;
    }


    @Override
    public void run() {
      LOG.debug("Search task started for contributor ", myContributor);
      try {
        myContributor.fetchElements(myPattern, myUseNonProjectItems, filter, myIndicator,
                                    element -> {
                                      try {
                                        int priority = myContributor.getElementPriority(element, myPattern);
                                        boolean added = myAccumulator.addElement(element, myContributor, priority);
                                        if (!added) {
                                          myAccumulator.setContributorHasMore(myContributor, true);
                                        }
                                        return added;
                                      }
                                      catch (InterruptedException e) {
                                        LOG.warn("Search task was interrupted");
                                        return false;
                                      }
                                    });
        myAccumulator.contributorFinished(myContributor);
      } finally {
        finishCallback.run();
      }
      LOG.debug("Search task finished for contributor ", myContributor);
    }
  }

  public static class ElementInfo {
    private final int priority;
    private final Object element;
    private final SearchEverywhereContributor<?> contributor;

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

  public static abstract class ResultsAccumulator {
    protected final Map<SearchEverywhereContributor<?>, Collection<MultithreadSearcher.ElementInfo>> sections;
    protected final MultithreadSearcher.Listener myListener;
    protected final Executor myNotificationExecutor;

    ResultsAccumulator(Map<SearchEverywhereContributor<?>, Collection<MultithreadSearcher.ElementInfo>> sections, Listener listener,
                              Executor notificationExecutor) {
      this.sections = sections;
      myListener = listener;
      myNotificationExecutor = notificationExecutor;
    }

    protected Collection<Pair<SearchEverywhereContributor<?>, ElementInfo>> findSameElements(Object element) {
      Collection<Pair<SearchEverywhereContributor<?>, ElementInfo>> res = new ArrayList<>();
      sections.forEach((contributor, objects) ->
                         objects.stream()
                           .filter(info -> Objects.equals(element, info.element))
                           .forEach(info -> res.add(Pair.create(contributor, info)))
      );

      return res;
    }

    public abstract boolean addElement(Object element, SearchEverywhereContributor<?> contributor, int priority) throws InterruptedException;
    public abstract void contributorFinished(SearchEverywhereContributor<?> contributor);
    public abstract void setContributorHasMore(SearchEverywhereContributor<?> contributor, boolean hasMore);
  }

  private static class ShowMoreResultsAccumulator extends ResultsAccumulator {
    private final SearchEverywhereContributor<?> myExpandedContributor;
    private final int myNewLimit;
    private volatile boolean hasMore;

    public ShowMoreResultsAccumulator(Map<SearchEverywhereContributor<?>, Collection<ElementInfo>> alreadyFound, SearchEverywhereContributor<?> contributor,
                                      int newLimit, Listener listener, Executor notificationExecutor) {
      super(new ConcurrentHashMap<>(alreadyFound), listener, notificationExecutor);
      myExpandedContributor = contributor;
      myNewLimit = newLimit;
    }

    @Override
    public boolean addElement(Object element, SearchEverywhereContributor<?> contributor, int priority) {
      assert contributor == myExpandedContributor; // Only expanded contributor items allowed

      Collection<ElementInfo> section = sections.get(contributor);
      ElementInfo newElementInfo = new ElementInfo(element, priority, contributor);

      if (section.size() >= myNewLimit) {
        return false;
      }

      Collection<Pair<SearchEverywhereContributor<?>, ElementInfo>> sameElementsInfo = findSameElements(element);
      if (!sameElementsInfo.isEmpty() && sameElementsInfo.stream().allMatch(pair -> pair.second.getPriority() >= priority)) {
        LOG.debug(String.format("Element %s for contributor %s was skipped", element.toString(), contributor.getSearchProviderId()));
        return true;
      }

      section.add(newElementInfo);
      myNotificationExecutor.execute(() -> myListener.elementsAdded(Collections.singletonList(newElementInfo)));
      sameElementsInfo.stream()
        .filter(pair -> pair.second.getPriority() < priority)
        .forEach(pair -> {
          Collection<ElementInfo> list = sections.get(pair.first);
          list.remove(pair.second);
          myNotificationExecutor.execute(() -> myListener.elementsRemoved(Collections.singletonList(pair.second)));
          LOG.debug(String.format("Element %s for contributor %s is removed",pair.second.getElement().toString(), pair.second.getContributor().getSearchProviderId()));
        });
      return true;
    }

    @Override
    public void setContributorHasMore(SearchEverywhereContributor<?> contributor, boolean hasMore) {
      assert contributor == myExpandedContributor; // Only expanded contributor items allowed
      this.hasMore = hasMore;

    }

    @Override
    public void contributorFinished(SearchEverywhereContributor<?> contributor) {
      myNotificationExecutor.execute(() -> myListener.searchFinished(Collections.singletonMap(contributor, hasMore)));
    }
  }

  /**
   * Resulting list accumulator.
   */
  private static class FullSearchResultsAccumulator extends ResultsAccumulator {

    private final Map<SearchEverywhereContributor<?>, Integer> sectionsLimits;
    private final Map<SearchEverywhereContributor<?>, Condition> conditionsMap;
    private final Map<SearchEverywhereContributor<?>, Boolean> hasMoreMap = new ConcurrentHashMap<>();
    private final Set<SearchEverywhereContributor<?>> finishedContributorsSet = ContainerUtil.newConcurrentSet();
    private final Lock lock = new ReentrantLock();
    private volatile boolean mySearchFinished = false;

    FullSearchResultsAccumulator(Map<SearchEverywhereContributor<?>, Integer> contributorsAndLimits, Listener listener,
                                 Executor notificationExecutor) {
      super(contributorsAndLimits.entrySet().stream().collect(Collectors.toMap(entry -> entry.getKey(), entry -> new ArrayList<>(entry.getValue()))),
            listener, notificationExecutor);
      sectionsLimits = contributorsAndLimits;
      conditionsMap = contributorsAndLimits.keySet().stream().collect(Collectors.toMap(Function.identity(), c -> lock.newCondition()));
    }

    @Override
    public void setContributorHasMore(SearchEverywhereContributor<?> contributor, boolean hasMore) {
      hasMoreMap.put(contributor, hasMore);
    }

    @Override
    public boolean addElement(Object element, SearchEverywhereContributor<?> contributor, int priority) throws InterruptedException {
      ElementInfo newElementInfo = new ElementInfo(element, priority, contributor);
      Condition condition = conditionsMap.get(contributor);
      Collection<ElementInfo> section = sections.get(contributor);
      int limit = sectionsLimits.get(contributor);

      lock.lock();
      try {
        while (section.size() >= limit && !mySearchFinished) {
          condition.await();
        }

        if (mySearchFinished) {
          return false;
        }

        Collection<Pair<SearchEverywhereContributor<?>, ElementInfo>> sameElementsInfo = findSameElements(element);
        if (!sameElementsInfo.isEmpty() && sameElementsInfo.stream().allMatch(pair -> pair.second.getPriority() >= priority)) {
          LOG.debug(String.format("Element %s for contributor %s was skipped", element.toString(), contributor.getSearchProviderId()));
          return true;
        }

        section.add(newElementInfo);
        myNotificationExecutor.execute(() -> myListener.elementsAdded(Collections.singletonList(newElementInfo)));
        sameElementsInfo.stream()
          .filter(pair -> pair.second.getPriority() < priority)
          .forEach(pair -> {
            Collection<ElementInfo> list = sections.get(pair.first);
            Condition listCondition = conditionsMap.get(pair.first);
            list.remove(pair.second);
            myNotificationExecutor.execute(() -> myListener.elementsRemoved(Collections.singletonList(pair.second)));
            LOG.debug(String.format("Element %s for contributor %s is removed",pair.second.getElement().toString(), pair.second.getContributor().getSearchProviderId()));
            listCondition.signal();
          });

        if (section.size() >= limit) {
          stopSearchIfNeeded();
        }
        return true;
      } finally {
        lock.unlock();
      }
    }

    @Override
    public void contributorFinished(SearchEverywhereContributor<?> contributor) {
      lock.lock();
      try {
        finishedContributorsSet.add(contributor);
        stopSearchIfNeeded();
      } finally {
        lock.unlock();
      }
    }

    public void searchFinished() {
      myNotificationExecutor.execute(() -> myListener.searchFinished(hasMoreMap));
    }

    public void stop() {
      lock.lock();
      try {
        mySearchFinished = true;
        conditionsMap.values().forEach(Condition::signalAll);
      } finally {
        lock.unlock();
      }
    }

    /**
     * could be used only when current thread owns {@link lock}
     */
    private void stopSearchIfNeeded() {
      if (sections.keySet().stream().allMatch(contributor -> isContributorFinished(contributor))) {
        mySearchFinished = true;
        conditionsMap.values().forEach(Condition::signalAll);
      }
    }

    private boolean isContributorFinished(SearchEverywhereContributor<?> contributor) {
      if (finishedContributorsSet.contains(contributor)) {
        return true;
      }

      return sections.get(contributor).size() >= sectionsLimits.get(contributor);
    }
  }
}
