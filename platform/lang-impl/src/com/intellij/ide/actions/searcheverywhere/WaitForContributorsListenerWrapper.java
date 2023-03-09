// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.concurrency.EdtExecutorService;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

public class WaitForContributorsListenerWrapper implements SearchListener{

  private static final Logger LOG = Logger.getInstance(WaitForContributorsListenerWrapper.class);

  private static final long DEFAULT_WAIT_TIMEOUT = 3000;
  private static final long DEFAULT_THROTTLING_TIMEOUT = 100;

  private final Map<SearchEverywhereContributor<?>, Boolean> contributorsMap = new HashMap<>();
  private final ScheduledExecutorService executorService = EdtExecutorService.getScheduledExecutorInstance();
  private final SearchListModel listModel;
  private Future<?> flushFuture;
  private final long waitTimeout;
  private final long throttlingTimeout;
  private final SearchListener delegateListener;
  private final SearchEventsBuffer buffer = new SearchEventsBuffer();

  public WaitForContributorsListenerWrapper(SearchListener delegate, SearchListModel model, long waitTimeout, long throttlingTimeout) {
    delegateListener = delegate;
    listModel = model;
    this.waitTimeout = waitTimeout;
    this.throttlingTimeout = throttlingTimeout;
  }

  public WaitForContributorsListenerWrapper(SearchListener delegate, SearchListModel model) {
    this(delegate, model, DEFAULT_WAIT_TIMEOUT, DEFAULT_THROTTLING_TIMEOUT);
  }

  @Override
  public void searchStarted(@NotNull Collection<? extends SearchEverywhereContributor<?>> contributors) {
    resetState(contributors);
    delegateListener.searchStarted(contributors);
    long timeout = contributors.size() > 1 ? waitTimeout : throttlingTimeout;
    scheduleFlush(timeout);
  }

  @Override
  public void searchFinished(@NotNull Map<SearchEverywhereContributor<?>, Boolean> hasMoreContributors) {
    cancelScheduledFlush();
    buffer.flushBuffer(delegateListener);
    delegateListener.searchFinished(hasMoreContributors);
  }

  @Override
  public void elementsAdded(@NotNull List<? extends SearchEverywhereFoundElementInfo> list) {
    buffer.addElements(list);
    scheduleFlush(throttlingTimeout);
  }

  @Override
  public void elementsRemoved(@NotNull List<? extends SearchEverywhereFoundElementInfo> list) {
    buffer.removeElements(list);
    scheduleFlush(throttlingTimeout);
  }

  @Override
  public void contributorFinished(@NotNull SearchEverywhereContributor<?> contributor, boolean hasMore) {
    buffer.contributorFinished(contributor, hasMore);
    markContributorAndCheckFlush(contributor);
  }

  @Override
  public void contributorWaits(@NotNull SearchEverywhereContributor<?> contributor) {
    buffer.contributorWaits(contributor);
    markContributorAndCheckFlush(contributor);
  }

  private void markContributorAndCheckFlush(@NotNull SearchEverywhereContributor<?> contributor) {
    if (contributorsMap.get(contributor) != null) {
      contributorsMap.put(contributor, true);
      if (ContainerUtil.and(contributorsMap.values(), Boolean::booleanValue)) {
        cancelScheduledFlush();
        buffer.flushBuffer(delegateListener);
        listModel.freezeElements();
      }
    }
  }

  private void cancelScheduledFlush() {
    if (flushFuture != null) flushFuture.cancel(false);
  }

  private void scheduleFlush(long timeout) {
    if (flushFuture != null && !flushFuture.isDone()) return;

    Runnable command = () -> {
      logNonFinished();
      buffer.flushBuffer(delegateListener);
      listModel.freezeElements();
    };

    flushFuture = executorService.schedule(command, timeout, TimeUnit.MILLISECONDS);
  }

  private void logNonFinished() {
    contributorsMap.forEach((contributor, finished) -> {
      if (!finished) LOG.warn(String.format("Contributor (%s) did not finish search in timeout (%d). Maybe it should implement PossibleSlowContributor interface", contributor.getSearchProviderId(),
                                            waitTimeout));
    });
  }

  private void resetState(Collection<? extends SearchEverywhereContributor<?>> contributors) {
    cancelScheduledFlush();
    buffer.clearBuffer();
    Map<? extends SearchEverywhereContributor<?>, Boolean> map = contributors.stream()
      .filter(c -> !PossibleSlowContributor.checkSlow(c))
      .collect(Collectors.toMap(Function.identity(), c -> false));
    contributorsMap.clear();
    contributorsMap.putAll(map);
  }
}