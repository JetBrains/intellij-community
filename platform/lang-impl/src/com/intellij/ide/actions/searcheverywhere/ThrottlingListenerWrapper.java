// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere;

import com.intellij.openapi.Disposable;
import com.intellij.util.Alarm;
import com.intellij.util.concurrency.ThreadingAssertions;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.VisibleForTesting;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Implementation of {@link SearchListener} which decrease events rate and raise batch updates
 * each {@code throttlingDelay} milliseconds.
 * <br>
 * Not thread-safe and should be notified only in EDT
 */
@ApiStatus.Internal
public final class ThrottlingListenerWrapper implements SearchListener, Disposable {

  private static final int DEFAULT_THROTTLING_TIMEOUT = 100;

  private final int myThrottlingDelay;
  private final Alarm flushAlarm = new Alarm();
  private boolean flushScheduled;
  private final SearchEventsBuffer buffer = new SearchEventsBuffer();
  private final SearchListener delegateListener;

  @VisibleForTesting
  public ThrottlingListenerWrapper(int throttlingDelay, SearchListener delegate) {
    delegateListener = delegate;
    myThrottlingDelay = throttlingDelay;
  }

  @VisibleForTesting
  public ThrottlingListenerWrapper(SearchListener delegateListener) {
    this(DEFAULT_THROTTLING_TIMEOUT, delegateListener);
  }

  @Override
  public void dispose() {
    cancelScheduledFlush();
  }

  @Override
  public void elementsAdded(@NotNull List<? extends SearchEverywhereFoundElementInfo> list) {
    buffer.addElements(list);
    scheduleFlushBuffer();
  }

  @Override
  public void elementsRemoved(@NotNull List<? extends SearchEverywhereFoundElementInfo> list) {
    buffer.removeElements(list);
    scheduleFlushBuffer();
  }

  @Override
  public void searchStarted(@NotNull String pattern, @NotNull Collection<? extends SearchEverywhereContributor<?>> contributors) {
    buffer.clearBuffer();
    delegateListener.searchStarted(pattern, contributors);
  }

  @Override
  public void searchFinished(@NotNull Map<SearchEverywhereContributor<?>, Boolean> hasMoreContributors) {
    cancelScheduledFlush();
    buffer.flushBuffer(delegateListener);
    delegateListener.searchFinished(hasMoreContributors);
  }

  @Override
  public void contributorWaits(@NotNull SearchEverywhereContributor<?> contributor) {
    buffer.contributorWaits(contributor);
  }

  @Override
  public void contributorFinished(@NotNull SearchEverywhereContributor<?> contributor, boolean hasMore) {
    buffer.contributorFinished(contributor, hasMore);
  }

  private void scheduleFlushBuffer() {
    ThreadingAssertions.assertEventDispatchThread();

    Runnable flushTask = () -> {
      ThreadingAssertions.assertEventDispatchThread();
      if (!flushScheduled) return;
      flushScheduled = false;
      buffer.flushBuffer(delegateListener);
    };

    if (!flushScheduled) {
      flushAlarm.addRequest(flushTask, myThrottlingDelay);
      flushScheduled = true;
    }
  }

  private void cancelScheduledFlush() {
    ThreadingAssertions.assertEventDispatchThread();
    flushAlarm.cancelAllRequests();
    flushScheduled = false;
  }
}
