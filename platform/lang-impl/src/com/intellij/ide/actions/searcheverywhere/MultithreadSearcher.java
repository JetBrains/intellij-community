// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.searcheverywhere;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.util.Pair;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.hash.EqualityPolicy;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.*;
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
    ProgressIndicator indicator = new ProgressIndicatorBase();
    ResultsAccumulator accumulator = new FullSearchResultsAccumulator(contributorsAndLimits, myListener, indicator, myNotificationExecutor);
    Phaser phaser = new Phaser();

    Runnable finisherTask = createFinisherTask(phaser, accumulator);
    for (SearchEverywhereContributor<?> contributor : contributorsAndLimits.keySet()) {
      SearchEverywhereContributorFilter<?> filter = filterSupplier.apply(contributor);
      Runnable task = createSearchTask(pattern, useNonProjectItems, accumulator, indicator, phaser, contributor, filter);
      ApplicationManager.getApplication().executeOnPooledThread(task);
    }
    ApplicationManager.getApplication().executeOnPooledThread(finisherTask);

    return indicator;
  }

  public ProgressIndicator findMoreItems(Map<SearchEverywhereContributor<?>, Collection<ElementInfo>> alreadyFound, String pattern,
                                         boolean useNonProjectItems, SearchEverywhereContributor<?> contributorToExpand, int newLimit,
                                         Function<SearchEverywhereContributor<?>, SearchEverywhereContributorFilter<?>> filterSupplier) {
    ProgressIndicator indicator = new ProgressIndicatorBase();
    ResultsAccumulator accumulator = new ShowMoreResultsAccumulator(alreadyFound, contributorToExpand, newLimit, myListener,
                                                                    indicator, myNotificationExecutor);
    SearchEverywhereContributorFilter<?> filter = filterSupplier.apply(contributorToExpand);
    Runnable task = createSearchTask(pattern, useNonProjectItems, accumulator, indicator, null, contributorToExpand, filter);
    ApplicationManager.getApplication().executeOnPooledThread(task);

    return indicator;
  }

  @NotNull
  private static <F> Runnable createSearchTask(String pattern, boolean useNonProjectItems, ResultsAccumulator accumulator,
                                               ProgressIndicator indicator, Phaser phaser, SearchEverywhereContributor<F> contributor,
                                               SearchEverywhereContributorFilter<?> filter) {
    Runnable finalCallback = () -> {};
    if (phaser != null) {
      phaser.register();
      finalCallback = () -> phaser.arrive();
    }
    ContributorSearchTask<F> task = new ContributorSearchTask<>(contributor, pattern, (SearchEverywhereContributorFilter<F>)filter,
                                                                useNonProjectItems, accumulator, indicator, finalCallback);
    return ConcurrencyUtil.underThreadNameRunnable("SE-SearchTask", task);
  }

  private static Runnable createFinisherTask(Phaser phaser, ResultsAccumulator accumulator) {
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
                                        //todo !!!
                                        //int priority = myContributor.getPriority(element);
                                        //EqualityPolicy<Object> eqPolicy = myContributor.getEqualityPolicy();

                                        int priority = myContributor.getElementPriority(element, myPattern);
                                        EqualityPolicy<Object> eqPolicy = (EqualityPolicy<Object>) EqualityPolicy.CANONICAL;
                                        boolean added = myAccumulator.addElement(element, myContributor, priority, eqPolicy);
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

  private static class Event {
    private static final int REMOVE = 0;
    private static final int ADD = 1;
    private static final int FINISH = 2;

    private final int type;
    private final ElementInfo element;

    public Event(int type, ElementInfo element) {
      this.type = type;
      this.element = element;
    }
  }

  public static abstract class ResultsAccumulator {
    protected final Map<SearchEverywhereContributor<?>, Collection<MultithreadSearcher.ElementInfo>> sections;
    protected final MultithreadSearcher.Listener myListener;
    protected final ProgressIndicator myProgressIndicator;
    protected final Executor myNotificationExecutor;

    ResultsAccumulator(Map<SearchEverywhereContributor<?>, Collection<MultithreadSearcher.ElementInfo>> sections, Listener listener,
                              ProgressIndicator indicator, Executor notificationExecutor) {
      this.sections = sections;
      myListener = listener;
      myProgressIndicator = indicator;
      myNotificationExecutor = notificationExecutor;
    }

    protected Collection<Pair<SearchEverywhereContributor<?>, ElementInfo>> findSameElements(Object element, EqualityPolicy<Object> policy) {
      Collection<Pair<SearchEverywhereContributor<?>, ElementInfo>> res  = new ArrayList<>();
      sections.forEach((contributor, objects) ->
                         objects.stream()
                           .filter(info -> policy.isEqual(element, info.element))
                           .forEach(info -> res.add(Pair.create(contributor, info)))
      );

      return res;
    }

    public abstract boolean addElement(Object element, SearchEverywhereContributor<?> contributor, int priority, EqualityPolicy<Object> policy) throws InterruptedException;
    public abstract void contributorFinished(SearchEverywhereContributor<?> contributor);
    public abstract void searchFinished();
    public abstract void setContributorHasMore(SearchEverywhereContributor<?> contributor, boolean hasMore);
  }

  private static class ShowMoreResultsAccumulator extends ResultsAccumulator {
    private final SearchEverywhereContributor<?> myExpandedContributor;
    private final int myNewLimit;
    private volatile boolean hasMore;

    public ShowMoreResultsAccumulator(Map<SearchEverywhereContributor<?>, Collection<ElementInfo>> alreadyFound, SearchEverywhereContributor<?> contributor,
                                      int newLimit, Listener listener, ProgressIndicator indicator, Executor notificationExecutor) {
      super(new ConcurrentHashMap<>(alreadyFound), listener, indicator, notificationExecutor);
      myExpandedContributor = contributor;
      myNewLimit = newLimit;
    }

    @Override
    public boolean addElement(Object element, SearchEverywhereContributor<?> contributor, int priority, EqualityPolicy<Object> policy) {
      assert contributor == myExpandedContributor; // Only expanded contributor items allowed

      Collection<ElementInfo> section = sections.get(contributor);
      ElementInfo newElementInfo = new ElementInfo(element, priority, contributor);

      if (section.size() >= myNewLimit) {
        return false;
      }

      Collection<Pair<SearchEverywhereContributor<?>, ElementInfo>> sameElementsInfo = findSameElements(element, policy);
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

    @Override
    public void searchFinished() {/*not used*/}


  }

  /**
   * Resulting list accumulator.
   */
  private static class FullSearchResultsAccumulator extends ResultsAccumulator {

    private static final long NOTIFICATION_THROTTLING_TIME = 200;

    private final Map<SearchEverywhereContributor<?>, Integer> sectionsLimits;
    private final Map<SearchEverywhereContributor<?>, Condition> conditionsMap;
    private final Map<SearchEverywhereContributor<?>, Boolean> hasMoreMap = new ConcurrentHashMap<>();
    private final Set<SearchEverywhereContributor<?>> finishedContributorsSet = ContainerUtil.newConcurrentSet();
    private final Lock lock = new ReentrantLock();
    private volatile boolean mySearchFinished = false;

    private final BlockingQueue<Event> updateEventQueue = new LinkedBlockingQueue<>();

    FullSearchResultsAccumulator(Map<SearchEverywhereContributor<?>, Integer> contributorsAndLimits, Listener listener,
                       ProgressIndicator indicator, Executor notificationExecutor) {
      super(contributorsAndLimits.entrySet().stream().collect(Collectors.toMap(entry -> entry.getKey(), entry -> new ArrayList<>(entry.getValue()))),
            listener, indicator, notificationExecutor);
      sectionsLimits = contributorsAndLimits;
      conditionsMap = contributorsAndLimits.keySet().stream().collect(Collectors.toMap(Function.identity(), c -> lock.newCondition()));

      //todo throttling
      ApplicationManager.getApplication().executeOnPooledThread(createNotifierTask());
    }

    @Override
    public void setContributorHasMore(SearchEverywhereContributor<?> contributor, boolean hasMore) {
      hasMoreMap.put(contributor, hasMore);
    }

    @Override
    public boolean addElement(Object element, SearchEverywhereContributor<?> contributor, int priority,
                           EqualityPolicy<Object> policy) throws InterruptedException {
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

        Collection<Pair<SearchEverywhereContributor<?>, ElementInfo>> sameElementsInfo = findSameElements(element, policy);
        if (!sameElementsInfo.isEmpty() && sameElementsInfo.stream().allMatch(pair -> pair.second.getPriority() >= priority)) {
          LOG.debug(String.format("Element %s for contributor %s was skipped", element.toString(), contributor.getSearchProviderId()));
          return true;
        }

        section.add(newElementInfo);
        updateEventQueue.add(new Event(Event.ADD, newElementInfo));
        sameElementsInfo.stream()
          .filter(pair -> pair.second.getPriority() < priority)
          .forEach(pair -> {
            Collection<ElementInfo> list = sections.get(pair.first);
            Condition listCondition = conditionsMap.get(pair.first);
            list.remove(pair.second);
            updateEventQueue.add(new Event(Event.REMOVE, pair.second));
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

    @Override
    public void searchFinished() {
      updateEventQueue.add(new Event(Event.FINISH, null));
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

    private Runnable createNotifierTask() {
      return ConcurrencyUtil.underThreadNameRunnable("SE-NotifierTask", () -> {
        try {
          boolean finished = false;
          while (!finished) {
            Event event = updateEventQueue.take();

            if (myProgressIndicator.isCanceled()) {
              return;
            }

            long startTime = System.currentTimeMillis();
            Collection<Event> events = new ArrayList<>();
            while (event != null) {
              if (event.type == Event.FINISH) {
                finished = true;
              }
              events.add(event);

              if (System.currentTimeMillis() - startTime > NOTIFICATION_THROTTLING_TIME) {
                break;
              }
              event = updateEventQueue.poll();
            }

            notifyListener(events, Event.ADD, items -> myListener.elementsAdded(items));
            notifyListener(events, Event.REMOVE, items -> myListener.elementsRemoved(items));
            notifyListener(events, Event.FINISH, ignrd -> myListener.searchFinished(hasMoreMap));
          }
        }
        catch (InterruptedException e) {
          LOG.debug("Notification process interrupted");
        }
        LOG.debug("Notification thread finished");
      });
    }

    private void notifyListener(Collection<Event> events, int type, Consumer<List<ElementInfo>> notifyCallback) {
      List<ElementInfo> items = events.stream()
        .filter(e -> e.type == type)
        .map(e -> e.element)
        .collect(Collectors.toList());

      if (!items.isEmpty()) {
        myNotificationExecutor.execute(() -> notifyCallback.consume(items));
      }
    }
  }
}
