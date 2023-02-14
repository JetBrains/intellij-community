// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

import static java.util.Objects.requireNonNull;

/**
 * Join few {@linkplain EventWatcher} under one umbrella
 */
public class CompositeEventWatcher implements EventWatcher, Disposable {
  private final EventWatcher[] watchers;

  public CompositeEventWatcher(final @NotNull EventWatcher @NotNull ... watchers) {
    this.watchers = watchers.clone();
    for (int i = 0; i < watchers.length; i++) {
      final EventWatcher watcher = watchers[i];
      requireNonNull(watcher, "watchers must not be null, but watcher[" + i + "] is null");
      if (watcher instanceof Disposable) {
        Disposable disposable = (Disposable)watcher;
        Disposer.register(this, disposable);
      }
    }
  }

  @Override
  public void runnableTaskFinished(final @NotNull Runnable runnable,
                                   final long waitedInQueueNs,
                                   final int queueSize,
                                   final long executionDurationNs,
                                   final boolean wasInSkippedItems) {
    for (EventWatcher watcher : watchers) {
      watcher.runnableTaskFinished(runnable, waitedInQueueNs, queueSize, executionDurationNs, wasInSkippedItems);
    }
  }

  @Override
  public void edtEventStarted(final @NotNull AWTEvent event,
                              final long startedAtMs) {
    for (EventWatcher watcher : watchers) {
      watcher.edtEventStarted(event, startedAtMs);
    }
  }

  @Override
  public void edtEventFinished(final @NotNull AWTEvent event,
                               final long finishedAtMs) {
    for (EventWatcher watcher : watchers) {
      watcher.edtEventFinished(event, finishedAtMs);
    }
  }

  @Override
  public void logTimeMillis(final @NotNull String processId,
                            final long startedAtMs,
                            final @NotNull Class<? extends Runnable> runnableClass) {
    for (EventWatcher watcher : watchers) {
      watcher.logTimeMillis(processId, startedAtMs, runnableClass);
    }
  }

  @Override
  public void reset() {
    for (EventWatcher watcher : watchers) {
      watcher.reset();
    }
  }

  @Override
  public void dispose() {
    //for (EventWatcher watcher : watchers) {
    //  if (watcher instanceof Disposable) {
    //    final Disposable disposable = (Disposable)watcher;
    //    Disposer.dispose(disposable);
    //  }
    //}
  }
}
