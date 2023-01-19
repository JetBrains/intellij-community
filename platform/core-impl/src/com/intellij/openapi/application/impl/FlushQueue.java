// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl;

import com.intellij.codeWithMe.ClientId;
import com.intellij.diagnostic.EventWatcher;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diagnostic.ThrottledLogger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.Condition;
import com.intellij.util.ExceptionUtil;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import org.jetbrains.annotations.*;

import javax.swing.*;

import static java.util.concurrent.TimeUnit.MINUTES;

final class FlushQueue {
  private static final Logger LOG = Logger.getInstance(FlushQueue.class);
  private static final ThrottledLogger THROTTLED_LOG = new ThrottledLogger(LOG, MINUTES.toMillis(1));

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
      final RunnableInfo info = new RunnableInfo(runnable, modalityState, expired, queueSize);
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

      while (true) {
        final RunnableInfo info = myQueue.pollFirst();
        if (info == null) {
          return null;
        }
        if (info.expired.value(null)) {
          continue;
        }
        if (!currentModality.dominates(info.modalityState)) {
          requestFlush(); // in case someone wrote "invokeLater { UIUtil.dispatchAllInvocationEvents(); }"
          return info;
        }
        //MAYBE RC: probably better to copy 'info' on re-appending the tasks back to the myQueue
        //          (in .reincludeSkippedItems()) and also reset queueSize/queuedTimeNs fields.
        //          This way we got queue loading info 'cleared' (kind of) from bypassing influence,
        //          i.e. re-appended tasks will look as-if they were just added -- which is not strictly true,
        //          but it will disturb waiting times much less then current approach there skipped/not skipped
        //          tasks waiting times stats are merged together.
        mySkippedItems.add(info.wasSkipped());
      }
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

        //RC: ExceptionAnalyzer reports negative values here, but it is not clear there do they come from.
        //    The reasons I could think of now are:
        //    1) oddities of .nanoTime() behavior under different CPU power-saving modes
        //    2) changing .nanoTime() origin due to thead being relocated to another CPU
        //    3) long overflow in (end-start) expression.
        //    those are straightforward reasons, but 1-2 was mostly solved (it seems to me) in a modern
        //    hardware/software, and 3 is hard to expect in our use-cases. Hence, negative values could be
        //    due to some other code bug I don't see right now. Safeguarding here prevents errors down the
        //    stack, but it also shifts value statistics
        final long waitedTimeInQueueNs_safe = waitedInQueueNs >= 0 ? waitedInQueueNs : 0;
        final long executionDurationNs_safe = executionDurationNs >= 0 ? executionDurationNs : 0;

        watcher.runnableTaskFinished(runnable,
                                     waitedTimeInQueueNs_safe,
                                     info.queueSize,
                                     executionDurationNs_safe, info.wasInSkippedItems);

        if (waitedInQueueNs < 0 || executionDurationNs<0) {
          //maybe logs give us some hints about why the values are negative:
          THROTTLED_LOG.info("waitedInQueueNs(" + waitedInQueueNs + ") | executionDurationNs(" + executionDurationNs + ") is negative -> unexpected state");
        }
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
    /** How many items were in queue at the moment this item was enqueued */
    private final int queueSize;
    private final boolean wasInSkippedItems;

    @Async.Schedule
    RunnableInfo(final @NotNull Runnable runnable,
                 final @NotNull ModalityState modalityState,
                 final @NotNull Condition<?> expired,
                 final int queueSize) {
      this(runnable, modalityState, expired, ClientId.getCurrentValue(),
           queueSize, System.nanoTime(), /* wasInSkippedItems: */ false);
    }

    @Async.Schedule
    private RunnableInfo(final @NotNull Runnable runnable,
                         final @NotNull ModalityState modalityState,
                         final @NotNull Condition<?> expired,
                         final @NotNull String clientId,
                         final int queueSize,
                         final long queuedTimeNs,
                         final boolean wasInSkippedItems) {
      this.runnable = runnable;
      this.modalityState = modalityState;
      this.expired = expired;
      this.clientId = clientId;
      this.queuedTimeNs = queuedTimeNs;
      this.queueSize = queueSize;
      this.wasInSkippedItems = wasInSkippedItems;
    }

    public RunnableInfo wasSkipped() {
      return new RunnableInfo(
        runnable, modalityState, expired,
        clientId, queueSize, queuedTimeNs,
        /*wasInSkippedItems: */ true
      );
    }

    @Override
    @NonNls
    public String toString() {
      return "[runnable: " + runnable + "; state=" + modalityState + (expired.value(null) ? "; expired" : "") + "]{queued at: " +
             queuedTimeNs + " ns, " + queueSize + " items were in front of}{wasSkipped: "+wasInSkippedItems+"}";
    }
  }
}
