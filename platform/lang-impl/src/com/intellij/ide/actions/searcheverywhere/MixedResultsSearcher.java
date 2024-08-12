// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere;

import com.intellij.concurrency.ConcurrentCollectionFactory;
import com.intellij.concurrency.SensitiveProgressWrapper;
import com.intellij.ide.actions.searcheverywhere.SEResultsEqualityProvider.SEEqualElementsActionType;
import com.intellij.ide.actions.searcheverywhere.statistics.SearchingProcessStatisticsCollector;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.advanced.AdvancedSettings;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author msokolov
 */
final class MixedResultsSearcher implements SESearcher {

  private static final Logger LOG = Logger.getInstance(MixedResultsSearcher.class);

  private final @NotNull SearchListener myListener;
  private final @NotNull Executor myNotificationExecutor;
  private final @NotNull SEResultsEqualityProvider myEqualityProvider;

  /**
   * Creates MultiThreadSearcher with search results {@link SearchListener} and specifies executor which going to be used to call listener methods.
   * Use this constructor when you for example need to receive listener events only in AWT thread
   * @param listener {@link SearchListener} to get notifications about searching process
   * @param notificationExecutor searcher guarantees that all listener methods will be called only through this executor
   * @param equalityProviders collection of equality providers that checks if found elements are already in the search results
   */
  MixedResultsSearcher(@NotNull SearchListener listener,
                       @NotNull Executor notificationExecutor,
                       @NotNull Collection<? extends SEResultsEqualityProvider> equalityProviders) {
    myListener = listener;
    myNotificationExecutor = notificationExecutor;
    myEqualityProvider = SEResultsEqualityProvider.composite(equalityProviders);
  }

  @Override
  public ProgressIndicator search(@NotNull Map<? extends SearchEverywhereContributor<?>, Integer> contributorsAndLimits,
                                  @NotNull String pattern) {
    LOG.debug("Search started for pattern [", pattern, "]");

    if (pattern.isEmpty()) {
      if (ApplicationManager.getApplication().isUnitTestMode()) {
        contributorsAndLimits = Collections.emptyMap(); //empty search string is not allowed for tests
      }
      else {
        contributorsAndLimits = ContainerUtil.filter(contributorsAndLimits, c -> c.isEmptyPatternSupported());
      }
    }

    Map<? extends SearchEverywhereContributor<?>, Integer> map = contributorsAndLimits;
    Function<ProgressIndicator, ResultsAccumulator> accumulatorSupplier = indicator ->
      new ResultsAccumulator(map, myEqualityProvider, myListener, myNotificationExecutor, indicator);

    return performSearch(contributorsAndLimits.keySet(), pattern, accumulatorSupplier);
  }

  @Override
  public ProgressIndicator findMoreItems(@NotNull Map<? extends SearchEverywhereContributor<?>, Collection<SearchEverywhereFoundElementInfo>> alreadyFound,
                                         @NotNull Map<? extends SearchEverywhereContributor<?>, Integer> contributorsAndLimits,
                                         @NotNull String pattern) {
    Function<ProgressIndicator, ResultsAccumulator> accumulatorSupplier = indicator ->
      new ResultsAccumulator(alreadyFound, contributorsAndLimits, myEqualityProvider, myListener, myNotificationExecutor, indicator);

    return performSearch(contributorsAndLimits.keySet(), pattern, accumulatorSupplier);
  }

  private static @NotNull ProgressIndicator performSearch(@NotNull Collection<? extends SearchEverywhereContributor<?>> contributors,
                                                          @NotNull String pattern,
                                                          @NotNull Function<? super ProgressIndicator, ? extends ResultsAccumulator> accumulatorSupplier) {
    ProgressIndicator indicator;
    ResultsAccumulator accumulator;
    if (!contributors.isEmpty()) {
      CountDownLatch latch = new CountDownLatch(contributors.size());
      ProgressIndicatorWithCancelListener indicatorWithCancelListener = new ProgressIndicatorWithCancelListener();
      accumulator = accumulatorSupplier.apply(indicatorWithCancelListener);
      accumulator.searchStarted(pattern);

      for (SearchEverywhereContributor<?> contributor : contributors) {
        Runnable task = createSearchTask(pattern, accumulator,
                                         indicatorWithCancelListener, contributor, () -> latch.countDown());
        ApplicationManager.getApplication().executeOnPooledThread(task);
      }

      Runnable finisherTask = createFinisherTask(latch, accumulator, indicatorWithCancelListener);
      Future<?> finisherFeature = ApplicationManager.getApplication().executeOnPooledThread(finisherTask);
      indicatorWithCancelListener.setCancelCallback(() -> {
        accumulator.stop();
        finisherFeature.cancel(true);
      });
      indicator = indicatorWithCancelListener;
    }
    else {
      indicator = new ProgressIndicatorBase();
      accumulator = accumulatorSupplier.apply(indicator);
      accumulator.searchStarted(pattern);
    }

    indicator.start();
    if (contributors.isEmpty()) {
      indicator.stop();
      accumulator.searchFinished();
    }
    return indicator;
  }

  private static @NotNull Runnable createSearchTask(String pattern,
                                                    ResultsAccumulator accumulator,
                                                    ProgressIndicator indicator,
                                                    SearchEverywhereContributor<?> contributor,
                                                    Runnable finalCallback) {
    //noinspection unchecked
    ContributorSearchTask<?> task = new ContributorSearchTask<>(
      (SearchEverywhereContributor<Object>)contributor, pattern,
      accumulator, indicator, finalCallback);
    return ConcurrencyUtil.underThreadNameRunnable("SE-SearchTask-" + contributor.getSearchProviderId(), task);
  }

  private static Runnable createFinisherTask(CountDownLatch latch, ResultsAccumulator accumulator, ProgressIndicator indicator) {
    return ConcurrencyUtil.underThreadNameRunnable("SE-FinisherTask", () -> {
      try {
        latch.await();
        if (!indicator.isCanceled()) {
          accumulator.searchFinished();
        }
        indicator.stop();
      }
      catch (InterruptedException e) {
        LOG.debug("Finisher interrupted before search process is finished");
      }
    });
  }

  private static final class ContributorSearchTask<Item> implements Runnable {

    private final ResultsAccumulator myAccumulator;
    private final Runnable finishCallback;

    private final SearchEverywhereContributor<Item> myContributor;
    private final String myPattern;
    private final ProgressIndicator myIndicator;

    private ContributorSearchTask(SearchEverywhereContributor<Item> contributor,
                                  String pattern,
                                  ResultsAccumulator accumulator,
                                  ProgressIndicator indicator,
                                  Runnable callback) {
      myContributor = contributor;
      myPattern = pattern;
      myAccumulator = accumulator;
      myIndicator = indicator;
      finishCallback = callback;
    }


    @Override
    public void run() {
      LOG.debug("Search task started for contributor ", myContributor);
      SearchingProcessStatisticsCollector.searchStarted(myContributor);
      try {
        boolean repeat;
        do {
          ProgressIndicator wrapperIndicator = new SensitiveProgressWrapper(myIndicator);
          try {
            if (myContributor instanceof WeightedSearchEverywhereContributor) {
              ((WeightedSearchEverywhereContributor<Item>)myContributor).fetchWeightedElements(myPattern, wrapperIndicator,
                                                                                               descriptor -> processFoundItem(
                                                                                                 descriptor.getItem(),
                                                                                                 descriptor.getWeight(),
                                                                                                 wrapperIndicator));
            }
            else {
              myContributor.fetchElements(myPattern, wrapperIndicator,
                                          element -> {
                                            int priority = myContributor
                                              .getElementPriority(Objects.requireNonNull(element), myPattern);
                                            return processFoundItem(element, priority, wrapperIndicator);
                                          });
            }
          }
          catch (ProcessCanceledException ignore) {}
          repeat = !myIndicator.isCanceled() && wrapperIndicator.isCanceled();
        }
        while (repeat);

        if (myIndicator.isCanceled()) {
          return;
        }
        myAccumulator.contributorFinished(myContributor);
      }
      finally {
        finishCallback.run();
      }
      LOG.debug("Search task finished for contributor ", myContributor);
    }

    private boolean processFoundItem(Item element, int priority, ProgressIndicator wrapperIndicator) {
      try {
        if (element == null) {
          LOG.debug("Skip null element");
          return true;
        }

        reportElementOnce();

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
    }

    private boolean firstElementReported;
    private void reportElementOnce() {
      if (firstElementReported) return;
      firstElementReported = true;
      SearchingProcessStatisticsCollector.elementFound(myContributor);
    }
  }

  private static final class ResultsAccumulator {

    private final Map<? extends SearchEverywhereContributor<?>, Collection<SearchEverywhereFoundElementInfo>> mySections;
    private final SearchListener myListener;
    private final Executor myNotificationExecutor;
    private final SEResultsEqualityProvider myEqualityProvider;
    private final ProgressIndicator myProgressIndicator;
    private final Map<? extends SearchEverywhereContributor<?>, Integer> sectionsLimits;
    private final Map<? extends SearchEverywhereContributor<?>, Condition> conditionsMap;
    private final Map<SearchEverywhereContributor<?>, Boolean> hasMoreMap = new ConcurrentHashMap<>();
    private final Set<SearchEverywhereContributor<?>> finishedContributorsSet = ConcurrentCollectionFactory.createConcurrentSet();
    private final Lock lock = new ReentrantLock();
    private volatile boolean mySearchFinished = false;

    ResultsAccumulator(Map<? extends SearchEverywhereContributor<?>, Integer> contributorsAndLimits,
                       SEResultsEqualityProvider equalityProvider, SearchListener listener, Executor notificationExecutor,
                       ProgressIndicator progressIndicator) {
      this(contributorsAndLimits.entrySet().stream().collect(Collectors.toMap(entry -> entry.getKey(), entry -> new ArrayList<>(entry.getValue()))),
           contributorsAndLimits, equalityProvider, listener, notificationExecutor, progressIndicator);
    }

    public void searchStarted(@NotNull String pattern) {
      runInNotificationExecutor(() -> myListener.searchStarted(pattern, sectionsLimits.keySet()));
    }

    ResultsAccumulator(Map<? extends SearchEverywhereContributor<?>, Collection<SearchEverywhereFoundElementInfo>> alreadyFound,
                       Map<? extends SearchEverywhereContributor<?>, Integer> contributorsAndLimits,
                       SEResultsEqualityProvider equalityProvider,
                       SearchListener listener,
                       Executor notificationExecutor,
                       ProgressIndicator progressIndicator) {
      mySections = alreadyFound;
      myEqualityProvider = equalityProvider;
      myListener = listener;
      myNotificationExecutor = notificationExecutor;
      myProgressIndicator = progressIndicator;

      sectionsLimits = new HashMap<>(contributorsAndLimits);
      conditionsMap = contributorsAndLimits.keySet().stream().collect(Collectors.toMap(Function.identity(), c -> lock.newCondition()));
    }

    public void setContributorHasMore(SearchEverywhereContributor<?> contributor, boolean hasMore) {
      hasMoreMap.put(contributor, hasMore);
    }

    public boolean addElement(Object element, SearchEverywhereContributor<?> contributor, int priority, ProgressIndicator indicator)
      throws InterruptedException {
      final var mlService = SearchEverywhereMlService.getInstance();
      final SearchEverywhereFoundElementInfo newElementInfo;
      if (mlService == null) {
        newElementInfo = new SearchEverywhereFoundElementInfo(element, priority, contributor);
      }
      else {
        newElementInfo = mlService.createFoundElementInfo(contributor, element, priority);
      }

      Condition condition = conditionsMap.get(contributor);
      Collection<SearchEverywhereFoundElementInfo> section = mySections.get(contributor);
      int limit = sectionsLimits.get(contributor);

      lock.lock();
      try {
        boolean isNotified = false;
        while (section.size() >= limit && !mySearchFinished) {
          indicator.checkCanceled();
          ProgressManager.checkCanceled();
          if (!isNotified) {
            runInNotificationExecutor(() -> myListener.contributorWaits(contributor));
            isNotified = true;
          }
          condition.await(100, TimeUnit.MILLISECONDS);
        }

        if (mySearchFinished) {
          return false;
        }

        List<SearchEverywhereFoundElementInfo> alreadyFoundItems = mySections.values().stream()
          .flatMap(Collection::stream)
          .collect(Collectors.toList());
        SEEqualElementsActionType action = myEqualityProvider.compareItems(newElementInfo, alreadyFoundItems);
        if (AdvancedSettings.getBoolean("search.everywhere.recent.at.top") && action instanceof SEEqualElementsActionType.Replace replaceAction) {
          action = fixReplaceAction(replaceAction);
        }
        if (action == SEEqualElementsActionType.Skip.INSTANCE) {
          LOG.debug(String.format("Element %s for contributor %s was skipped", element.toString(), contributor.getSearchProviderId()));
          return true;
        }

        section.add(newElementInfo);
        runInNotificationExecutor(() -> myListener.elementsAdded(Collections.singletonList(newElementInfo)));

        List<SearchEverywhereFoundElementInfo> toRemove = action instanceof SEEqualElementsActionType.Replace
                                                          ? ((SEEqualElementsActionType.Replace)action).getToBeReplaced()
                                                          : Collections.emptyList();

        toRemove.forEach(info -> {
          Collection<SearchEverywhereFoundElementInfo> list = mySections.get(info.getContributor());
          Condition listCondition = conditionsMap.get(info.getContributor());
          list.remove(info);
          LOG.debug(String.format("Element %s for contributor %s is removed", info.getElement().toString(),
                                  info.getContributor().getSearchProviderId()));
          listCondition.signal();
        });
        runInNotificationExecutor(() -> myListener.elementsRemoved(toRemove));
        if (section.size() >= limit) {
          stopSearchIfNeeded();
        }
        return true;
      }
      finally {
        lock.unlock();
      }
    }

    private static SEEqualElementsActionType fixReplaceAction(SEEqualElementsActionType.Replace action) {
      String recentContributorID = RecentFilesSEContributor.class.getSimpleName();
      List<SearchEverywhereFoundElementInfo> updatedList = ContainerUtil.filter(
        action.getToBeReplaced(),
        info -> !recentContributorID.equals(info.getContributor().getSearchProviderId())
      );
      return updatedList.isEmpty() ? SEEqualElementsActionType.Skip.INSTANCE : new SEEqualElementsActionType.Replace(updatedList);
    }

    public void contributorFinished(SearchEverywhereContributor<?> contributor) {
      lock.lock();
      try {
        finishedContributorsSet.add(contributor);
        Boolean hasMore = hasMoreMap.get(contributor);
        runInNotificationExecutor(() -> myListener.contributorFinished(contributor, hasMore != null && hasMore.booleanValue()));
        stopSearchIfNeeded();
      }
      finally {
        lock.unlock();
      }
    }

    public void searchFinished() {
      runInNotificationExecutor(() -> myListener.searchFinished(hasMoreMap));
    }

    public void stop() {
      lock.lock();
      try {
        mySearchFinished = true;
        conditionsMap.values().forEach(Condition::signalAll);
      }
      finally {
        lock.unlock();
      }
    }

    /**
     * could be used only when current thread owns {@link #lock}
     */
    private void stopSearchIfNeeded() {
      if (mySections.keySet().stream().allMatch(contributor -> isContributorFinished(contributor))) {
        mySearchFinished = true;
        conditionsMap.values().forEach(Condition::signalAll);
      }
    }

    private boolean isContributorFinished(SearchEverywhereContributor<?> contributor) {
      if (finishedContributorsSet.contains(contributor)) {
        return true;
      }

      Integer limit = sectionsLimits.get(contributor);
      return limit == null || mySections.get(contributor).size() >= limit;
    }

    private void runInNotificationExecutor(Runnable runnable) {
      myNotificationExecutor.execute(() -> {
        if (!myProgressIndicator.isCanceled()) {
          runnable.run();
        }
      });
    }
  }

  private static final class ProgressIndicatorWithCancelListener extends ProgressIndicatorBase {

    private volatile Runnable cancelCallback = () -> {};

    private void setCancelCallback(Runnable cancelCallback) {
      this.cancelCallback = cancelCallback;
    }

    @Override
    protected void onRunningChange() {
      if (isCanceled()) {
        cancelCallback.run();
      }
    }
  }
}
