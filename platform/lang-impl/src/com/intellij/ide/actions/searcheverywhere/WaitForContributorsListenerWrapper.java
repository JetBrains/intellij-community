// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere;

import com.intellij.openapi.Disposable;
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
import java.util.function.Supplier;
import java.util.stream.Collectors;

public final class WaitForContributorsListenerWrapper implements SearchListener, Disposable {

  private static final Logger LOG = Logger.getInstance(WaitForContributorsListenerWrapper.class);

  static final long DEFAULT_WAIT_TIMEOUT_MS = 3000;
  static final long DEFAULT_THROTTLING_TIMEOUT_MS = 100;

  private final Map<SearchEverywhereContributor<?>, Boolean> contributorsMap = new HashMap<>();
  private final ScheduledExecutorService executorService = EdtExecutorService.getScheduledExecutorInstance();
  private final SearchListModel listModel;
  private Future<?> flushFuture;
  private final long waitTimeoutMs;
  private final long throttlingTimeoutMs;
  private final Supplier<String> mySearchPattern;
  private final SearchListener delegateListener;
  private final SearchEventsBuffer buffer = new SearchEventsBuffer();

  public WaitForContributorsListenerWrapper(@NotNull SearchListener delegate, @NotNull SearchListModel model,
                                            long waitTimeoutMs, long throttlingTimeoutMs,
                                            @NotNull Supplier<String> searchPattern) {
    delegateListener = delegate;
    listModel = model;
    this.waitTimeoutMs = waitTimeoutMs;
    this.throttlingTimeoutMs = throttlingTimeoutMs;
    mySearchPattern = searchPattern;
  }

  @Override
  public void dispose() {
    cancelScheduledFlush();
  }

  @Override
  public void searchStarted(@NotNull String pattern, @NotNull Collection<? extends SearchEverywhereContributor<?>> contributors) {
    resetState(contributors);
    delegateListener.searchStarted(pattern, contributors);
    long timeout = contributors.size() > 1 ? waitTimeoutMs : throttlingTimeoutMs;
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
    scheduleFlush(throttlingTimeoutMs);
  }

  @Override
  public void elementsRemoved(@NotNull List<? extends SearchEverywhereFoundElementInfo> list) {
    buffer.removeElements(list);
    scheduleFlush(throttlingTimeoutMs);
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

  private void scheduleFlush(long timeoutMs) {
    if (flushFuture != null && !flushFuture.isDone()) return;

    Runnable command = () -> {
      logNonFinished();
      buffer.flushBuffer(delegateListener);
      listModel.freezeElements();
    };

    flushFuture = executorService.schedule(command, timeoutMs, TimeUnit.MILLISECONDS);
  }

  private void logNonFinished() {
    contributorsMap.forEach((contributor, finished) -> {
      if (!finished) {
        LOG.warn("Contributor '" + contributor.getSearchProviderId() +
                 "' did not finish search for '" + mySearchPattern.get() + "'" +
                 " in " + waitTimeoutMs +"ms. Maybe it should implement PossibleSlowContributor interface?");
      }
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