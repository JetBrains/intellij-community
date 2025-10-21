// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl;

import com.intellij.concurrency.ContextAwareRunnable;
import com.intellij.concurrency.ThreadContext;
import com.intellij.diagnostic.EventWatcher;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diagnostic.ThrottledLogger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.Condition;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.concurrency.ThreadingAssertions;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.jetbrains.annotations.*;

import javax.swing.*;

import static java.util.concurrent.TimeUnit.MINUTES;

final class FlushQueue {
  private static final Logger LOG = Logger.getInstance(FlushQueue.class);
  private static final ThrottledLogger THROTTLED_LOG = new ThrottledLogger(LOG, MINUTES.toMillis(1));

  private ObjectArrayList<RunnableInfo> mySkippedItems = new ObjectArrayList<>(100); //guarded by getQueueLock()
  private final BulkArrayQueue<RunnableInfo> myQueue = new BulkArrayQueue<>();  //guarded by getQueueLock()

  private void flushNow() {
    ThreadContext.resetThreadContext(() -> {
      ThreadingAssertions.assertEventDispatchThread();
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
        if (InvocationUtil.priorityEventPending() || System.currentTimeMillis() - startTime > 5) {
          synchronized (getQueueLock()) {
            requestFlush();
          }
          break;
        }
      }
      return null;
    });
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

  @Override
  public String toString() {
    synchronized (getQueueLock()) {
      return "LaterInvocator.FlushQueue size=" + myQueue.size() + "; FLUSHER_SCHEDULED=" + FLUSHER_SCHEDULED;
    }
  }

  private @Nullable RunnableInfo pollNextEvent() {
    ModalityState currentModality = LaterInvocator.getCurrentModalityState();

    while (true) {
      final RunnableInfo info;
      synchronized (getQueueLock()) {
        info = myQueue.pollFirst();
        if (info == null) {
          return null;
        }

        if (!currentModality.accepts(info.modalityState)) {
          //MAYBE RC: probably better to copy 'info' on re-appending the tasks back to the myQueue
          //          (in .reincludeSkippedItems()) and also reset queueSize/queuedTimeNs fields.
          //          This way we got queue loading info 'cleared' (kind of) from bypassing influence,
          //          i.e. re-appended tasks will look as-if they were just added -- which is not strictly true,
          //          but it will disturb waiting times much less than the current approach there skipped/not skipped
          //          tasks waiting times stats are merged together.
          mySkippedItems.add(info.wasSkipped());
          continue;
        }
      }
      // we need to check `expired` outside of the queue lock,
      // otherwise we may have a deadlock if `expired` tries to run a read action
      // see IJPL-190911
      if (!info.expired.value(null)) {
        requestFlush(); // in case someone wrote "invokeLater { UIUtil.dispatchAllInvocationEvents(); }"
        return info;
      }
    }
  }

  private static void runNextEvent(@NotNull RunnableInfo info) {
    final EventWatcher watcher = EventWatcher.getInstanceOrNull();
    final long waitingFinishedNs = System.nanoTime();
    try {
      info.runnable.run();
    }
    catch (ProcessCanceledException ignored) {

    }
    catch (Throwable t) {
      if (ApplicationManager.getApplication().isUnitTestMode()) {
        ExceptionUtil.rethrow(t);
      }
      if (!Logger.shouldRethrow(t)) {
        LOG.error(t);
      }
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

        if (waitedInQueueNs < 0 || executionDurationNs < 0) {
          //maybe logs give us some hints about why the values are negative:
          THROTTLED_LOG.info(
            "waitedInQueueNs(" + waitedInQueueNs + ") | executionDurationNs(" + executionDurationNs + ") is negative -> unexpected state");
        }
      }
    }
  }

  void reincludeSkippedItems() {
    ThreadingAssertions.assertEventDispatchThread();
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
    ThreadingAssertions.assertEventDispatchThread();
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

  private final Runnable FLUSH_NOW = (ContextAwareRunnable)this::flushNow;

  boolean isFlushNow(@NotNull Runnable runnable) {
    return runnable == FLUSH_NOW;
  }

  private static class RunnableInfo {
    private final @NotNull Runnable runnable;
    private final @NotNull ModalityState modalityState;
    private final @NotNull Condition<?> expired;
    private final long queuedTimeNs;
    /** How many items were in queue at the moment this item was enqueued */
    private final int queueSize;
    private final boolean wasInSkippedItems;

    @Async.Schedule
    RunnableInfo(final @NotNull Runnable runnable,
                 final @NotNull ModalityState modalityState,
                 final @NotNull Condition<?> expired,
                 final int queueSize) {
      this(runnable, modalityState, expired,
           queueSize, System.nanoTime(), /* wasInSkippedItems: */ false);
    }

    @Async.Schedule
    private RunnableInfo(final @NotNull Runnable runnable,
                         final @NotNull ModalityState modalityState,
                         final @NotNull Condition<?> expired,
                         final int queueSize,
                         final long queuedTimeNs,
                         final boolean wasInSkippedItems) {
      this.runnable = runnable;
      this.modalityState = modalityState;
      this.expired = expired;
      this.queuedTimeNs = queuedTimeNs;
      this.queueSize = queueSize;
      this.wasInSkippedItems = wasInSkippedItems;
    }

    public RunnableInfo wasSkipped() {
      return new RunnableInfo(
        runnable, modalityState, expired,
        queueSize, queuedTimeNs,
        /*wasInSkippedItems: */ true
      );
    }

    @Override
    public @NonNls String toString() {
      return "[runnable: " + runnable + "; state=" + modalityState + (expired.value(null) ? "; expired" : "") + "]{queued at: " +
             queuedTimeNs + " ns, " + queueSize + " items were in front of}{wasSkipped: " + wasInSkippedItems + "}";
    }
  }
}
