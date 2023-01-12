// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.searcheverywhere;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.util.Alarm;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

/**
 * Implementation of {@link SearchListener} which decrease events rate and raise batch updates
 * each {@code throttlingDelay} milliseconds.
 * <br>
 * Not thread-safe and should be notified only in EDT
 */
class ThrottlingListenerWrapper extends BufferingListenerWrapper {

  private static final int DEFAULT_THROTTLING_TIMEOUT = 100;

  public final int myThrottlingDelay;

  private final Alarm flushAlarm = new Alarm();
  private boolean flushScheduled;

  ThrottlingListenerWrapper(int throttlingDelay, SearchListener delegateListener) {
    super(delegateListener);
    myThrottlingDelay = throttlingDelay;
  }

  ThrottlingListenerWrapper(SearchListener delegateListener) {
    this(DEFAULT_THROTTLING_TIMEOUT, delegateListener);
  }

  @Override
  public void clearBuffer() {
    super.clearBuffer();
    cancelScheduledFlush();
  }

  @Override
  public void elementsAdded(@NotNull List<? extends SearchEverywhereFoundElementInfo> list) {
    super.elementsAdded(list);
    scheduleFlushBuffer();
  }

  @Override
  public void elementsRemoved(@NotNull List<? extends SearchEverywhereFoundElementInfo> list) {
    super.elementsRemoved(list);
    scheduleFlushBuffer();
  }

  @Override
  public void searchFinished(@NotNull Map<SearchEverywhereContributor<?>, Boolean> hasMoreContributors) {
    super.searchFinished(hasMoreContributors);
    cancelScheduledFlush();
  }

  @Override
  public void contributorFinished(@NotNull SearchEverywhereContributor<?> contributor, boolean hasMore) { }

  private void scheduleFlushBuffer() {
    ApplicationManager.getApplication().assertIsDispatchThread();

    Runnable flushTask = () -> {
      ApplicationManager.getApplication().assertIsDispatchThread();
      if (!flushScheduled) return;
      flushScheduled = false;
      flushBuffer();
    };

    if (!flushScheduled) {
      flushAlarm.addRequest(flushTask, myThrottlingDelay);
      flushScheduled = true;
    }
  }

  private void cancelScheduledFlush() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    flushAlarm.cancelAllRequests();
    flushScheduled = false;
  }
}
