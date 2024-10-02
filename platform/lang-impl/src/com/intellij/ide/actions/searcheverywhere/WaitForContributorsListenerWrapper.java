// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.advanced.AdvancedSettings;
import com.intellij.util.concurrency.EdtExecutorService;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@ApiStatus.Internal
public final class WaitForContributorsListenerWrapper implements SearchListener, Disposable {

  private static final Logger LOG = Logger.getInstance(WaitForContributorsListenerWrapper.class);

  static final long DEFAULT_WAIT_TIMEOUT_MS = 2000;
  static final long DEFAULT_THROTTLING_TIMEOUT_MS = 100;

  private final Map<SearchEverywhereContributor<?>, Boolean> contributorsMap = new HashMap<>();
  private final ScheduledExecutorService executorService = EdtExecutorService.getScheduledExecutorInstance();
  private final SearchListModel listModel;
  private Future<?> throttlingFlushFuture;
  private Future<?> timeoutFlushFuture;
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
    cancelAllFlushTasks();
  }

  @Override
  public void searchStarted(@NotNull String pattern, @NotNull Collection<? extends SearchEverywhereContributor<?>> contributors) {
    resetState(contributors);
    delegateListener.searchStarted(pattern, contributors);
    scheduleTimeoutFlush();
    scheduleThrottlingFlush();
  }

  @Override
  public void searchFinished(@NotNull Map<SearchEverywhereContributor<?>, Boolean> hasMoreContributors) {
    cancelAllFlushTasks();
    buffer.flushBuffer(delegateListener);
    delegateListener.searchFinished(hasMoreContributors);
  }

  @Override
  public void elementsAdded(@NotNull List<? extends SearchEverywhereFoundElementInfo> list) {
    list = processFastPassItems(list);
    if (list.isEmpty()) return;

    buffer.addElements(list);
    scheduleThrottlingFlush();
    ContainerUtil.map2SetNotNull(list, info -> info.contributor).forEach(this::markContributorArrived);
  }

  @Override
  public void elementsRemoved(@NotNull List<? extends SearchEverywhereFoundElementInfo> list) {
    buffer.removeElements(list);
    scheduleThrottlingFlush();
  }

  @Override
  public void contributorFinished(@NotNull SearchEverywhereContributor<?> contributor, boolean hasMore) {
    buffer.contributorFinished(contributor, hasMore);
    markContributorArrived(contributor);
    scheduleThrottlingFlush();
  }

  @Override
  public void contributorWaits(@NotNull SearchEverywhereContributor<?> contributor) {
    buffer.contributorWaits(contributor);
    markContributorArrived(contributor);
    scheduleThrottlingFlush();
  }

  @ApiStatus.Experimental
  @Override
  public void standardSearchFoundNoResults(@NotNull SearchEverywhereContributor<?> contributor) {
    delegateListener.standardSearchFoundNoResults(contributor);
  }

  /**
   * Method send results from {@link RecentFilesSEContributor} to results immediately without waiting. And returns the rest of items
   * from passed {@code list} parameter
   */
  private List<? extends SearchEverywhereFoundElementInfo> processFastPassItems(@NotNull List<? extends SearchEverywhereFoundElementInfo> list) {
    if (!AdvancedSettings.getBoolean("search.everywhere.recent.at.top")) return list;

    Map<Boolean, Set<SearchEverywhereFoundElementInfo>> map = ContainerUtil.classify(list.iterator(), info -> isFastPassContributor(info.getContributor()));
    Set<SearchEverywhereFoundElementInfo> fastPassItems = map.get(true);
    if (fastPassItems != null) {
      delegateListener.elementsAdded(new ArrayList<>(fastPassItems));
      listModel.freezeElements();
    }
    Set<SearchEverywhereFoundElementInfo> nonFast = map.get(false);
    return nonFast != null ? new ArrayList<>(nonFast) : Collections.emptyList();
  }

  private static boolean isFastPassContributor(SearchEverywhereContributor<?> contributor) {
    if (contributor instanceof RecentFilesSEContributor) return true;
    if (contributor instanceof PSIPresentationBgRendererWrapper wrapper) return wrapper.getDelegate() instanceof RecentFilesSEContributor;
    return false;
  }

  private void markContributorArrived(@NotNull SearchEverywhereContributor<?> contributor) {
    if (contributorsMap.get(contributor) != null) {
      contributorsMap.put(contributor, true);
    }
  }

  private static void cancelFlushTask(Future<?> task) {
    if (task != null) task.cancel(false);
  }

  private void cancelAllFlushTasks() {
    cancelFlushTask(throttlingFlushFuture);
    cancelFlushTask(timeoutFlushFuture);
  }

  private void scheduleTimeoutFlush() {
    if (timeoutFlushFuture != null && !timeoutFlushFuture.isDone()) return;
    Runnable command = () -> {
      cancelFlushTask(throttlingFlushFuture);
      flushBuffer(true);
      contributorsMap.keySet().forEach(c -> contributorsMap.put(c, true));
    };
    timeoutFlushFuture = executorService.schedule(command, waitTimeoutMs, TimeUnit.MILLISECONDS);
  }

  private void scheduleThrottlingFlush() {
    if (throttlingFlushFuture != null && !throttlingFlushFuture.isDone()) return;
    Runnable command = () -> {
      if (ContainerUtil.and(contributorsMap.values(), Boolean::booleanValue)) {
        cancelFlushTask(timeoutFlushFuture);
        flushBuffer(false);
      }
    };
    throttlingFlushFuture = executorService.schedule(command, throttlingTimeoutMs, TimeUnit.MILLISECONDS);
  }

  private void flushBuffer(boolean logNotFinished) {
    if (logNotFinished) logNonFinished();
    buffer.flushBuffer(delegateListener);
    listModel.freezeElements();
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
    cancelAllFlushTasks();
    buffer.clearBuffer();
    Map<? extends SearchEverywhereContributor<?>, Boolean> map = contributors.stream()
      .filter(EssentialContributor::checkEssential)
      .collect(Collectors.toMap(Function.identity(), c -> false));
    contributorsMap.clear();
    contributorsMap.putAll(map);
  }
}