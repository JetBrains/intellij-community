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

public class WaitForContributorsListenerWrapper extends BufferingListenerWrapper {

  private static final Logger LOG = Logger.getInstance(WaitForContributorsListenerWrapper.class);

  private static final long TIMEOUT = 3000;

  private final Map<SearchEverywhereContributor<?>, Boolean> contributorsMap = new HashMap<>();
  private final ScheduledExecutorService executorService = EdtExecutorService.getScheduledExecutorInstance();
  private final SearchListModel listModel;
  private Future<?> flushFuture;
  private boolean useBuffer = true;

  public WaitForContributorsListenerWrapper(SearchListener delegate, SearchListModel model) {
    super(delegate);
    listModel = model;
  }

  @Override
  public void elementsAdded(@NotNull List<? extends SearchEverywhereFoundElementInfo> list) {
    if (useBuffer) {
      super.elementsAdded(list);
    }
    else {
      myDelegateListener.elementsAdded(list);
    }
  }

  @Override
  public void elementsRemoved(@NotNull List<? extends SearchEverywhereFoundElementInfo> list) {
    if (useBuffer) {
      super.elementsRemoved(list);
    }
    else {
      myDelegateListener.elementsRemoved(list);
    }
  }

  @Override
  public void searchStarted(@NotNull Collection<? extends SearchEverywhereContributor<?>> contributors) {
    super.searchStarted(contributors);
    resetState(contributors);
    if (useBuffer) {
      flushFuture = scheduleFlash();
    }
  }

  @Override
  public void searchFinished(@NotNull Map<SearchEverywhereContributor<?>, Boolean> hasMoreContributors) {
    cancelScheduledFlush();
    super.searchFinished(hasMoreContributors);
  }

  @Override
  public void contributorFinished(@NotNull SearchEverywhereContributor<?> contributor, boolean hasMore) {
    markContributorAndCheckFlush(contributor);
  }

  @Override
  public void contributorWaits(@NotNull SearchEverywhereContributor<?> contributor) {
    markContributorAndCheckFlush(contributor);
    super.contributorWaits(contributor);
  }

  private void markContributorAndCheckFlush(@NotNull SearchEverywhereContributor<?> contributor) {
    if (contributorsMap.get(contributor) != null && useBuffer) {
      contributorsMap.put(contributor, true);
      if (ContainerUtil.and(contributorsMap.values(), Boolean::booleanValue)) {
        cancelScheduledFlush();
        flushBuffer();
        useBuffer = false;
        listModel.freezeElements();
      }
    }
  }

  private void cancelScheduledFlush() {
    if (flushFuture == null) return;
    flushFuture.cancel(false);
  }

  private Future<?> scheduleFlash() {
    Runnable command = () -> {
      logNonFinished();
      flushBuffer();
      useBuffer = false;
      listModel.freezeElements();
    };

    return executorService.schedule(command, TIMEOUT, TimeUnit.MILLISECONDS);
  }

  private void logNonFinished() {
    contributorsMap.forEach((contributor, finished) -> {
      if (!finished) LOG.warn(String.format("Contributor (%s) did not finish search in timeout (%d). Maybe it should implement PossibleSlowContributor interface", contributor.getSearchProviderId(), TIMEOUT));
    });
  }

  private void resetState(Collection<? extends SearchEverywhereContributor<?>> contributors) {
    cancelScheduledFlush();
    clearBuffer();
    Map<? extends SearchEverywhereContributor<?>, Boolean> map = contributors.stream()
      .filter(c -> !PossibleSlowContributor.checkSlow(c))
      .collect(Collectors.toMap(Function.identity(), c -> false));
    contributorsMap.clear();
    contributorsMap.putAll(map);
    useBuffer = contributors.size() > 1;
  }
}