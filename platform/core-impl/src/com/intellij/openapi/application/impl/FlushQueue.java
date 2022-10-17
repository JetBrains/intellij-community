// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl;

import com.intellij.codeWithMe.ClientId;
import com.intellij.diagnostic.EventWatcher;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.Condition;
import com.intellij.util.ExceptionUtil;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import org.jetbrains.annotations.*;

import javax.swing.*;

final class FlushQueue {
  private static final Logger LOG = Logger.getInstance(FlushQueue.class);
  private ObjectList<RunnableInfo> mySkippedItems = new ObjectArrayList<>(100); //guarded by getQueueLock()
  private final BulkArrayQueue<RunnableInfo> myQueue = new BulkArrayQueue<>();  //guarded by getQueueLock()

  private void flushNow() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    synchronized (getQueueLock()) {
      FLUSHER_SCHEDULED = false;
    }

    long startTime = System.currentTimeMillis();
    while (true) {
      RunnableInfo info = pollNextEvent();
      if (info == null) {
        break;
      }
      runNextEvent(info);
      if (System.currentTimeMillis() - startTime > 5) {
        synchronized (getQueueLock()) {
          requestFlush();
        }
        break;
      }
    }
  }

  private Object getQueueLock() {
    return myQueue;
  }

  void push(@NotNull ModalityState modalityState,
            @NotNull Condition<?> expired,
            @NotNull Runnable runnable) {
    synchronized (getQueueLock()) {
      final int queueSize = myQueue.size();
      RunnableInfo info = new RunnableInfo(runnable, modalityState, expired, queueSize);
      myQueue.enqueue(info);
      requestFlush();
    }
  }

  @TestOnly
  @NotNull
  Object getQueue() {
    synchronized (getQueueLock()) {
      // used by leak hunter as root, so we must not copy it here to another list
      // to avoid walking over obsolete queue
      return myQueue;
    }
  }

  // Extracted to have a capture point
  private static void doRun(@Async.Execute @NotNull RunnableInfo info) {
    try (AccessToken ignored = ClientId.withClientId(info.clientId)) {
      info.runnable.run();
    }
  }

  @Override
  public String toString() {
    synchronized (getQueueLock()) {
      return "LaterInvocator.FlushQueue size=" + myQueue.size() + "; FLUSHER_SCHEDULED=" + FLUSHER_SCHEDULED;
    }
  }

  @Nullable
  private RunnableInfo pollNextEvent() {
    synchronized (getQueueLock()) {
      ModalityState currentModality = LaterInvocator.getCurrentModalityState();

      RunnableInfo info;
      while (true) {
        info = myQueue.pollFirst();
        if (info == null) {
          break;
        }
        if (info.expired.value(null)) {
          continue;
        }
        if (!currentModality.dominates(info.modalityState)) {
          requestFlush(); // in case someone wrote "invokeLater { UIUtil.dispatchAllInvocationEvents(); }"
          break;
        }
        mySkippedItems.add(info);
      }

      return info;
    }
  }

  private static void runNextEvent(@NotNull RunnableInfo info) {
    final EventWatcher watcher = EventWatcher.getInstanceOrNull();
    final long waitingFinishedNs = System.nanoTime();
    try {
      doRun(info);
    }
    catch (ProcessCanceledException ignored) {

    }
    catch (Throwable t) {
      if (ApplicationManager.getApplication().isUnitTestMode()) {
        ExceptionUtil.rethrow(t);
      }
      LOG.error(t);
    }
    finally {
      if (watcher != null) {
        final Runnable runnable = info.runnable;
        final long executionFinishedNs = System.nanoTime();
        final long waitedInQueueNs = waitingFinishedNs - info.queuedTimeNs;
        final long executionDurationNs = executionFinishedNs - waitingFinishedNs;

        watcher.runnableTaskFinished(runnable, waitedInQueueNs, info.queueSize, executionDurationNs);
      }
    }
  }

  void reincludeSkippedItems() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    synchronized (getQueueLock()) {
      int size = mySkippedItems.size();
      if (size != 0) {
        myQueue.bulkEnqueueFirst(mySkippedItems);
        // .clear() may be expensive
        if (size < 100) {
          mySkippedItems.clear();
        }
        else {
          mySkippedItems = new ObjectArrayList<>(100);
        }
      }
      requestFlush();
    }
  }

  void purgeExpiredItems() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    synchronized (getQueueLock()) {
      reincludeSkippedItems();
      myQueue.removeAll(info -> info.expired.value(null));
      requestFlush();
    }
  }

  private boolean FLUSHER_SCHEDULED; // guarded by getQueueLock()

  // must be run under getQueueLock()
  private void requestFlush() {
    boolean shouldSchedule = !FLUSHER_SCHEDULED && !myQueue.isEmpty();
    if (shouldSchedule) {
      FLUSHER_SCHEDULED = true;
      SwingUtilities.invokeLater(FLUSH_NOW);
    }
  }

  private final Runnable FLUSH_NOW = this::flushNow;
  boolean isFlushNow(@NotNull Runnable runnable) {
    return runnable == FLUSH_NOW;
  }

  private static class RunnableInfo {
    @NotNull private final Runnable runnable;
    @NotNull private final ModalityState modalityState;
    @NotNull private final Condition<?> expired;
    @NotNull private final String clientId;
    private final long queuedTimeNs;
    /** How many items were in queue at the momen this item was enqueued */
    private final int queueSize;

    @Async.Schedule
    RunnableInfo(@NotNull Runnable runnable,
                 @NotNull ModalityState modalityState,
                 @NotNull Condition<?> expired,
                 final int queueSize) {
      this.runnable = runnable;
      this.modalityState = modalityState;
      this.expired = expired;
      this.clientId = ClientId.getCurrentValue();
      this.queuedTimeNs = System.nanoTime();
      this.queueSize = queueSize;
    }

    @Override
    @NonNls
    public String toString() {
      return "[runnable: " + runnable + "; state=" + modalityState + (expired.value(null) ? "; expired" : "") + "]{queued at: " +
             queuedTimeNs + " ns, "+queueSize+" items were in front of}";
    }
  }
}
