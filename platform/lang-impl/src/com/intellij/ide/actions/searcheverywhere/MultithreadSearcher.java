// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.searcheverywhere;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.util.Pair;
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

  @NotNull
  //private final Listener myEDTWrappedListener;


  private final Listener myListener;
  private final Executor myNotificationExecutor;

  /**
   * @param listener listener which will be notified when results list is changed or search is finished.
   *                 Searcher guarantees that this listener will be notified only in EDT
   */
  public MultithreadSearcher(@NotNull Listener listener, Executor notificationExecutor) {
    myListener = listener;
    myNotificationExecutor = notificationExecutor;
  }

  public MultithreadSearcher(@NotNull Listener listener) {
    this(listener, Runnable::run);
  }

  public ProgressIndicator search(Collection<SearchEverywhereContributor<?>> contributors,
                                  String pattern,
                                  boolean useNonProjectItems,
                                  Function<SearchEverywhereContributor<?>, SearchEverywhereContributorFilter<?>> filterSupplier,
                                  int limit) {
    ResultsAccumulator accumulator = new ResultsAccumulator(contributors, limit, myListener, myNotificationExecutor);
    ProgressIndicator indicator = new ProgressIndicatorBase();
    Phaser phaser = new Phaser();

    Runnable finisherTask = createFinisherTask(phaser, accumulator);
    for (SearchEverywhereContributor<?> contributor : contributors) {
      SearchEverywhereContributorFilter<?> filter = filterSupplier.apply(contributor);
      Runnable task = createSearchTask(pattern, useNonProjectItems, accumulator, indicator, phaser, contributor, filter);
      ApplicationManager.getApplication().executeOnPooledThread(task);
    }
    ApplicationManager.getApplication().executeOnPooledThread(finisherTask);

    return indicator;
  }

  @NotNull
  private static <F> Runnable createSearchTask(String pattern, boolean useNonProjectItems, ResultsAccumulator accumulator,
                                               ProgressIndicator indicator, Phaser phaser, SearchEverywhereContributor<F> contributor,
                                               SearchEverywhereContributorFilter<?> filter) {
    phaser.register();
    return new ContributorSearchTask<>(contributor, pattern, (SearchEverywhereContributorFilter<F>) filter, useNonProjectItems, accumulator,
                                       indicator, () -> phaser.arrive());
  }

  private static Runnable createFinisherTask(Phaser phaser, ResultsAccumulator accumulator) {
    phaser.register();
    return () -> {
      phaser.arriveAndAwaitAdvance();
      accumulator.searchFinished();
    };
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
      try {
        myContributor.fetchElements(myPattern, myUseNonProjectItems, filter, myIndicator,
                                    element -> {
                                      try {
                                        //todo !!!
                                        //int priority = myContributor.getPriority(element);
                                        //EqualityPolicy<Object> eqPolicy = myContributor.getEqualityPolicy();

                                        int priority = 0;
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
    }
  }

  public static class ElementInfo {
    private final int priority;
    private final Object element;
    private final SearchEverywhereContributor<?> contributor;

    private ElementInfo(Object element, int priority, SearchEverywhereContributor<?> contributor) {
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

  /**
   * Resulting list accumulator.
   */
  private static class ResultsAccumulator {

    private static final long NOTIFICATION_THROTTLING_TIME = 200;

    private final Map<SearchEverywhereContributor<?>,Collection<ElementInfo>> sections;
    private final Map<SearchEverywhereContributor<?>, Condition> conditionsMap;
    private final Map<SearchEverywhereContributor<?>, Boolean> hasMoreMap = new ConcurrentHashMap<>();
    private final Set<SearchEverywhereContributor<?>> finishedContributorsSet = ContainerUtil.newConcurrentSet();
    private final Lock lock = new ReentrantLock();
    private final int groupElementsLimit;
    private final Listener myListener;

    private volatile boolean mySearchFinished = false;
    private final Executor myNotificationExecutor;

    private final BlockingQueue<Event> updateEventQueue = new LinkedBlockingQueue<>();

    ResultsAccumulator(Collection<SearchEverywhereContributor<?>> contributors, int groupElementsLimit,
                       Listener listener, Executor notificationExecutor) {
      this.groupElementsLimit = groupElementsLimit;
      myListener = listener;
      myNotificationExecutor = notificationExecutor;
      sections = contributors.stream().collect(Collectors.toMap(Function.identity(), c -> new ArrayList<>(groupElementsLimit)));
      conditionsMap = contributors.stream().collect(Collectors.toMap(Function.identity(), c -> lock.newCondition()));

      //todo throttling
      ApplicationManager.getApplication().executeOnPooledThread(createNotifierTask());
    }

    public <F> void setContributorHasMore(SearchEverywhereContributor<F> contributor, boolean hasMore) {
      hasMoreMap.put(contributor, hasMore);
    }

    public boolean addElement(Object element, SearchEverywhereContributor<?> contributor, int priority,
                           EqualityPolicy<Object> policy) throws InterruptedException {
      ElementInfo newElementInfo = new ElementInfo(element, priority, contributor);
      Condition condition = conditionsMap.get(contributor);
      Collection<ElementInfo> section = sections.get(contributor);

      lock.lock();
      try {
        while (section.size() >= groupElementsLimit && !mySearchFinished) {
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

        if (section.size() >= groupElementsLimit) {
          stopSearchIfNeeded();
        }
        return true;
      } finally {
        lock.unlock();
      }
    }

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

      Collection<ElementInfo> list = sections.get(contributor);
      return list != null && list.size() >= groupElementsLimit;
    }

    private Collection<Pair<SearchEverywhereContributor<?>, ElementInfo>> findSameElements(Object element, EqualityPolicy<Object> policy) {
      Collection<Pair<SearchEverywhereContributor<?>, ElementInfo>> res  = new ArrayList<>();
      sections.forEach((contributor, objects) ->
                         objects.stream()
                           .filter(info -> policy.isEqual(element, info.element))
                           .forEach(info -> res.add(Pair.create(contributor, info)))
      );

      return res;
    }

    private Runnable createNotifierTask() {
      return () -> {
        try {
          boolean finished = false;
          while (!finished) {
            Event event = updateEventQueue.take();
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
      };
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
