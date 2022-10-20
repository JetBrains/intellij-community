// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.util.SystemProperties;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

@ApiStatus.Experimental
@ApiStatus.Internal
public interface EventWatcher {
  /**
   * Enable lightweight aggregated monitoring for FlushQueue/EventQueue. Default: true
   *
   * @see com.intellij.diagnostic.OtelReportingEventWatcher
   */
  boolean isEnabledAggregated = SystemProperties.getBooleanProperty("idea.event.queue.dispatch.report-aggregated-stats-to-otel", true);

  /**
   * Enable detailed (per-class) monitoring for FlushQueue/EventQueue. Default: false
   * @see com.intellij.diagnostic.DetailedEventWatcher
   */
  //NOTE: 'false' default value is  important since true value leads to initialization of EventWatcherToolWindowFactory,
  //      which fails CodeWithMe tests. Seems like a kind of (missed) cleanup issue, but I wasn't able to investigate
  //      it in details.
  boolean isEnabledDetailed = SystemProperties.getBooleanProperty("idea.event.queue.dispatch.listen", false);

  static boolean isDetailedWatcherEnabled() {
    return isEnabledDetailed;
  }

  static boolean isAggregatedWatcherEnabled() {
    return isEnabledAggregated;
  }

  static @Nullable EventWatcher getInstanceOrNull() {
    if (!isDetailedWatcherEnabled() && !isAggregatedWatcherEnabled()) {
      return null;
    }

    EventWatcher result = InstanceHolder.instance;
    if (result == null && LoadingState.CONFIGURATION_STORE_INITIALIZED.isOccurred()) {
      Application app = ApplicationManager.getApplication();
      if (app != null && !app.isDisposed()) {
        result = app.getService(EventWatcher.class);
        InstanceHolder.instance = result;
      }
    }
    return result;
  }

  /**
   * Reports timings for a single task {@linkplain com.intellij.openapi.application.impl.FlushQueue} (which is used
   * for our .invokeLater() implementation)
   * <br/>
   * BEWARE: other methods of the class accept time in _milliseconds_, while this method takes nanoseconds. This is
   * because 1 ms granularity is OK for outliers monitoring (which is for this class was created initially), but
   * is too coarse for detailed statistics (a lot of events probably wait/execute < 1 ms on a good machine)
   * <br/>
   *
   * @param waitedInQueueNs     time (in nanoseconds) task was waited in queue before EDT starts its execution.
   * @param queueSize           how many tasks were in queue at the moment this task was added.
   *                            Note: there is significant statistical difference between queue size seen by arrived
   *                            tasks, and queue size seen at random time moments -- those are two different kind of
   *                            samplings, they are equivalent only if arrivals are poisson-distributed, which are
   *                            rarely true for real workloads.
   * @param executionDurationNs how long did it take to execute the task (nanos)
   * @param wasInSkippedItems task was bypassed for a while because of modality state mismatch
   */
  @RequiresEdt
  void runnableTaskFinished(final @NotNull Runnable runnable,
                            final long waitedInQueueNs,
                            final int queueSize,
                            final long executionDurationNs,
                            final boolean wasInSkippedItems);

  @RequiresEdt
  void edtEventStarted(@NotNull AWTEvent event, long startedAtMs);

  @RequiresEdt
  void edtEventFinished(@NotNull AWTEvent event, long finishedAtMs);

  void reset();

  void logTimeMillis(@NotNull String processId,
                     long startedAtMs,
                     @NotNull Class<? extends Runnable> runnableClass);

  default void logTimeMillis(@NotNull String processId, long startedAtMs) {
    logTimeMillis(processId, startedAtMs, Runnable.class);
  }
}

final class InstanceHolder {
  static EventWatcher instance;

  private InstanceHolder() { }
}
