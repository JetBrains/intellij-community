// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.searcheverywhere;

import com.intellij.concurrency.SensitiveProgressWrapper;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Phaser;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.intellij.ide.actions.searcheverywhere.SEResultsEqualityProvider.Action.REPLACE;
import static com.intellij.ide.actions.searcheverywhere.SEResultsEqualityProvider.Action.SKIP;

/**
 * @author msokolov
 */
class MultithreadSearcher implements SESearcher {

  private static final Logger LOG = Logger.getInstance(MultithreadSearcher.class);

  @NotNull private final Listener myListener;
  @NotNull private final Executor myNotificationExecutor;
  @NotNull private final SEResultsEqualityProvider myEqualityProvider;

  /**
   * Creates MultithreadSearcher with search results {@link Listener} and specifies executor which going to be used to call listener methods.
   * Use this constructor when you for example need to receive listener events only in AWT thread
   * @param listener {@link Listener} to get notifications about searching process
   * @param notificationExecutor searcher guarantees that all listener methods will be called only through this executor
   * @param equalityProviders collection of equailty providers that checks if found elements is already in the search results
   */
  MultithreadSearcher(@NotNull Listener listener,
                      @NotNull Executor notificationExecutor,
                      @NotNull Collection<? extends SEResultsEqualityProvider> equalityProviders) {
    myListener = listener;
    myNotificationExecutor = notificationExecutor;
    myEqualityProvider = SEResultsEqualityProvider.composite(equalityProviders);
  }

  /**
   * Creates MultithreadSearcher with no guarantees about what thread gonna call {@code listener} methods.
   * In this case listener will be called from different threads, so you have to care about thread safety
   * @param listener {@link Listener} to get notifications about searching process
   * @param equalityProviders collection of equailty providers that checks if found elements is already in the search results
   */
  @SuppressWarnings("unused")
  MultithreadSearcher(@NotNull Listener listener, @NotNull Collection<? extends SEResultsEqualityProvider> equalityProviders) {
    this(listener, Runnable::run, equalityProviders);
  }

  /**
   * Starts searching process with given search parameters
   * @param contributorsAndLimits map of used searching contributors and maximum elements limit for them
   * @param pattern search pattern
   * @param useNonProjectItems flags indicating if non-projects items should be included in search results
   * @param filterSupplier supplier of {@link SearchEverywhereContributorFilter}'s for different search contributors
   * @return {@link ProgressIndicator} that could be used to track and/or cancel searching process
   */
  @Override
  public ProgressIndicator search(Map<SearchEverywhereContributor<?>, Integer> contributorsAndLimits, String pattern,
                                  boolean useNonProjectItems,
                                  Function<SearchEverywhereContributor<?>, SearchEverywhereContributorFilter<?>> filterSupplier) {
    LOG.debug("Search started for pattern [", pattern, "]");
    Phaser phaser = new Phaser();
    FullSearchResultsAccumulator accumulator = new FullSearchResultsAccumulator(contributorsAndLimits, myEqualityProvider, myListener, myNotificationExecutor);
    ProgressIndicator indicator = new ProgressIndicatorBase() {
      @Override
      protected void onRunningChange() {
        if (isCanceled()) {
          accumulator.stop();
          phaser.forceTermination();
        }
      }
    };
    indicator.start();

    Runnable finisherTask = createFinisherTask(phaser, accumulator, indicator);
    for (SearchEverywhereContributor<?> contributor : contributorsAndLimits.keySet()) {
      SearchEverywhereContributorFilter<?> filter = filterSupplier.apply(contributor);
      phaser.register();
      Runnable task = createSearchTask(pattern, useNonProjectItems, accumulator, indicator, contributor, filter, () -> phaser.arrive());
      ApplicationManager.getApplication().executeOnPooledThread(task);
    }
    ApplicationManager.getApplication().executeOnPooledThread(finisherTask);

    return indicator;
  }

  /**
   * Starts process of expanding (search for more elemetns) specified contributors section (when user choosed "more" item)
   * @param alreadyFound map of already found items for all used search contributors
   * @param pattern search pattern
   * @param useNonProjectItems flags indicating if non-projects items should be included in search results
   * @param contributorToExpand specifies {@link SearchEverywhereContributor} element which going to be expanded
   * @param newLimit new maximum elements limit for expanded contributor
   * @param filterSupplier supplier of {@link SearchEverywhereContributorFilter}'s for different search contributors
   * @return {@link ProgressIndicator} that could be used to track and/or cancel searching process
   */
  @Override
  public ProgressIndicator findMoreItems(Map<SearchEverywhereContributor<?>, Collection<ElementInfo>> alreadyFound, String pattern,
                                         boolean useNonProjectItems, SearchEverywhereContributor<?> contributorToExpand, int newLimit,
                                         Function<SearchEverywhereContributor<?>, SearchEverywhereContributorFilter<?>> filterSupplier) {
    ResultsAccumulator accumulator = new ShowMoreResultsAccumulator(alreadyFound, myEqualityProvider, contributorToExpand, newLimit, myListener, myNotificationExecutor);
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

  private static Runnable createFinisherTask(Phaser phaser, FullSearchResultsAccumulator accumulator, ProgressIndicator indicator) {
    phaser.register();

    return ConcurrencyUtil.underThreadNameRunnable("SE-FinisherTask", () -> {
      phaser.arriveAndAwaitAdvance();
      if (!indicator.isCanceled()) {
        accumulator.searchFinished();
      }
      indicator.stop();
    });
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
        boolean repeat = false;
        do {
          ProgressIndicator wrapperIndicator = new SensitiveProgressWrapper(myIndicator);
          try {
            myContributor.fetchElements(myPattern, myUseNonProjectItems, filter, wrapperIndicator,
                                        element -> {
                                          try {
                                            if (element == null) {
                                              LOG.debug("Skip null element");
                                              return true;
                                            }

                                            int priority = myContributor.getElementPriority(element, myPattern);
                                            boolean added = myAccumulator.addElement(element, myContributor, priority, wrapperIndicator);
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
          } catch (ProcessCanceledException pce) {}
          repeat = !myIndicator.isCanceled() && wrapperIndicator.isCanceled();
        } while (repeat);

        if (myIndicator.isCanceled()) {
          return;
        }
        myAccumulator.contributorFinished(myContributor);
      } finally {
        finishCallback.run();
      }
      LOG.debug("Search task finished for contributor ", myContributor);
    }
  }

  private static abstract class ResultsAccumulator {
    protected final Map<SearchEverywhereContributor<?>, Collection<ElementInfo>> sections;
    protected final MultithreadSearcher.Listener myListener;
    protected final Executor myNotificationExecutor;
    protected final SEResultsEqualityProvider myEqualityProvider;

    ResultsAccumulator(Map<SearchEverywhereContributor<?>, Collection<ElementInfo>> sections,
                       SEResultsEqualityProvider equalityProvider,
                       Listener listener,
                       Executor notificationExecutor) {
      this.sections = sections;
      myEqualityProvider = equalityProvider;
      myListener = listener;
      myNotificationExecutor = notificationExecutor;
    }

    protected Map<SEResultsEqualityProvider.Action, Collection<ElementInfo>> getActionsWithOtherElements(ElementInfo newElement) {
      Map<SEResultsEqualityProvider.Action, Collection<ElementInfo>> res = new EnumMap<>(SEResultsEqualityProvider.Action.class);
      res.put(REPLACE, new ArrayList<>());
      res.put(SKIP, new ArrayList<>());
      sections.values()
        .stream()
        .flatMap(Collection::stream)
        .forEach(info -> {
          SEResultsEqualityProvider.Action action = myEqualityProvider.compareItems(newElement, info);
          if (action != SEResultsEqualityProvider.Action.DO_NOTHING) {
            res.get(action).add(info);
          }
        });

      return res;
    }

    public abstract boolean addElement(Object element, SearchEverywhereContributor<?> contributor, int priority, ProgressIndicator indicator) throws InterruptedException;
    public abstract void contributorFinished(SearchEverywhereContributor<?> contributor);
    public abstract void setContributorHasMore(SearchEverywhereContributor<?> contributor, boolean hasMore);
  }

  private static class ShowMoreResultsAccumulator extends ResultsAccumulator {
    private final SearchEverywhereContributor<?> myExpandedContributor;
    private final int myNewLimit;
    private volatile boolean hasMore;

    ShowMoreResultsAccumulator(Map<SearchEverywhereContributor<?>, Collection<ElementInfo>> alreadyFound, SEResultsEqualityProvider equalityProvider,
                               SearchEverywhereContributor<?> contributor, int newLimit, Listener listener, Executor notificationExecutor) {
      super(new ConcurrentHashMap<>(alreadyFound), equalityProvider, listener, notificationExecutor);
      myExpandedContributor = contributor;
      myNewLimit = newLimit;
    }

    @Override
    public boolean addElement(Object element, SearchEverywhereContributor<?> contributor, int priority, ProgressIndicator indicator) {
      assert contributor == myExpandedContributor; // Only expanded contributor items allowed

      Collection<ElementInfo> section = sections.get(contributor);
      ElementInfo newElementInfo = new ElementInfo(element, priority, contributor);

      if (section.size() >= myNewLimit) {
        return false;
      }

      Map<SEResultsEqualityProvider.Action, Collection<ElementInfo>> otherElementsMap = getActionsWithOtherElements(newElementInfo);
      if (otherElementsMap.get(REPLACE).isEmpty() && !otherElementsMap.get(SKIP).isEmpty()) {
        LOG.debug(String.format("Element %s for contributor %s was skipped", element.toString(), contributor.getSearchProviderId()));
        return true;
      }

      section.add(newElementInfo);
      myNotificationExecutor.execute(() -> myListener.elementsAdded(Collections.singletonList(newElementInfo)));

      List<ElementInfo> toRemove = new ArrayList<>(otherElementsMap.get(REPLACE));
      toRemove.forEach(info -> {
        Collection<ElementInfo> list = sections.get(info.getContributor());
            list.remove(info);
            LOG.debug(String.format("Element %s for contributor %s is removed", info.getElement().toString(), info.getContributor().getSearchProviderId()));
      });
      myNotificationExecutor.execute(() -> myListener.elementsRemoved(toRemove));
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

  private static class FullSearchResultsAccumulator extends ResultsAccumulator {

    private final Map<SearchEverywhereContributor<?>, Integer> sectionsLimits;
    private final Map<SearchEverywhereContributor<?>, Condition> conditionsMap;
    private final Map<SearchEverywhereContributor<?>, Boolean> hasMoreMap = new ConcurrentHashMap<>();
    private final Set<SearchEverywhereContributor<?>> finishedContributorsSet = ContainerUtil.newConcurrentSet();
    private final Lock lock = new ReentrantLock();
    private volatile boolean mySearchFinished = false;

    FullSearchResultsAccumulator(Map<SearchEverywhereContributor<?>, Integer> contributorsAndLimits,
                                 SEResultsEqualityProvider equalityProvider, Listener listener, Executor notificationExecutor) {
      super(contributorsAndLimits.entrySet().stream().collect(Collectors.toMap(entry -> entry.getKey(), entry -> new ArrayList<>(entry.getValue()))),
            equalityProvider, listener, notificationExecutor);
      sectionsLimits = contributorsAndLimits;
      conditionsMap = contributorsAndLimits.keySet().stream().collect(Collectors.toMap(Function.identity(), c -> lock.newCondition()));
    }

    @Override
    public void setContributorHasMore(SearchEverywhereContributor<?> contributor, boolean hasMore) {
      hasMoreMap.put(contributor, hasMore);
    }

    @Override
    public boolean addElement(Object element, SearchEverywhereContributor<?> contributor, int priority, ProgressIndicator indicator) throws InterruptedException {
      ElementInfo newElementInfo = new ElementInfo(element, priority, contributor);
      Condition condition = conditionsMap.get(contributor);
      Collection<ElementInfo> section = sections.get(contributor);
      int limit = sectionsLimits.get(contributor);

      lock.lock();
      try {
        while (section.size() >= limit && !mySearchFinished) {
          indicator.checkCanceled();
          condition.await(100, TimeUnit.MILLISECONDS);
        }

        if (mySearchFinished) {
          return false;
        }

        Map<SEResultsEqualityProvider.Action, Collection<ElementInfo>> otherElementsMap = getActionsWithOtherElements(newElementInfo);
        if (otherElementsMap.get(REPLACE).isEmpty() && !otherElementsMap.get(SKIP).isEmpty()) {
          LOG.debug(String.format("Element %s for contributor %s was skipped", element.toString(), contributor.getSearchProviderId()));
          return true;
        }

        section.add(newElementInfo);
        myNotificationExecutor.execute(() -> myListener.elementsAdded(Collections.singletonList(newElementInfo)));

        List<ElementInfo> toRemove = new ArrayList<>(otherElementsMap.get(REPLACE));
        toRemove.forEach(info -> {
          Collection<ElementInfo> list = sections.get(info.getContributor());
          Condition listCondition = conditionsMap.get(info.getContributor());
          list.remove(info);
          LOG.debug(String.format("Element %s for contributor %s is removed", info.getElement().toString(), info.getContributor().getSearchProviderId()));
          listCondition.signal();
        });
        myNotificationExecutor.execute(() -> myListener.elementsRemoved(toRemove));

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
