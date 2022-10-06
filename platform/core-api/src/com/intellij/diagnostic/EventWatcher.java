// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

@ApiStatus.Experimental
@ApiStatus.Internal
public interface EventWatcher {
  static boolean isEnabled() {
    return InstanceHolder.isEnabled;
  }

  static @Nullable EventWatcher getInstanceOrNull() {
    if (!isEnabled()) {
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

  @RequiresEdt
  void runnableStarted(@NotNull Runnable runnable, long startedAt);

  @RequiresEdt
  void runnableFinished(@NotNull Runnable runnable, long finishedAt);

  @RequiresEdt
  void edtEventStarted(@NotNull AWTEvent event, long startedAt);

  @RequiresEdt
  void edtEventFinished(@NotNull AWTEvent event, long finishedAt);

  /**
   * Reports time (in nanoseconds) task was waited in queue before EDT starts its execution.
   * BEWARE: other methods accept time _in milliseconds_, while this method takes nanos -- this
   * is because 1 ms granularity is OK for outliers monitoring (which other methods do mostly),
   * but is too coarse for detailed statistics (most events probably wait < 1 ms on a good machine)
   * @param queueSize how many tasks were in queue at the moment this task was added.
   *                  Note: there is significant statistical difference between queue size seen by arrived
   *                  tasks, and queue size seen at random time moments -- those are two different kind of
   *                  samplings, they are equivalent only if arrivals are poisson-distributed, which are
   *                  rarely true for real workloads.
   */
  void logTimeWaitedInQueue(final @NotNull Runnable runnable,
                            final long waitedInQueueNs,
                            final int queueSize);

  void reset();

  void logTimeMillis(@NotNull String processId,
                     long startedAt,
                     @NotNull Class<? extends Runnable> runnableClass);

  default void logTimeMillis(@NotNull String processId, long startedAt) {
    logTimeMillis(processId, startedAt, Runnable.class);
  }
}

final class InstanceHolder {
  static EventWatcher instance;

  static final boolean isEnabled = Boolean.getBoolean("idea.event.queue.dispatch.listen");

  private InstanceHolder() { }
}