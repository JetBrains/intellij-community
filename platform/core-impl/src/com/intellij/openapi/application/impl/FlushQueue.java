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
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.util.*;

final class FlushQueue {
  private static final Logger LOG = Logger.getInstance(FlushQueue.class);
  private final Object LOCK = ObjectUtils.sentinel("FlushQueue");

  private List<RunnableInfo> mySkippedItems = new ArrayList<>(); //protected by LOCK

  private final Deque<RunnableInfo> myQueue = new ArrayDeque<>(); //protected by LOCK

  FlushQueue() {
  }

  private void flushNow() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    synchronized (LOCK) {
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
        break;
      }
    }
  }

  void push(@NotNull RunnableInfo runnableInfo) {
    synchronized (LOCK) {
      myQueue.offer(runnableInfo);
      requestFlush();
    }
  }

  @TestOnly
  @NotNull
  Collection<RunnableInfo> getQueue() {
    synchronized (LOCK) {
      // used by leak hunter as root, so we must not copy it here to another list
      // to avoid walking over obsolete queue
      return Collections.unmodifiableCollection(myQueue);
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
    synchronized (LOCK) {
      return "LaterInvocator.FlushQueue size=" + myQueue.size() + "; FLUSHER_SCHEDULED=" + FLUSHER_SCHEDULED;
    }
  }

  @Nullable
  private RunnableInfo pollNextEvent() {
    synchronized (LOCK) {
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
    EventWatcher watcher = EventWatcher.getInstanceOrNull();
    Runnable runnable = info.runnable;
    if (watcher != null) {
      watcher.runnableStarted(runnable, System.currentTimeMillis());
    }
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
        watcher.runnableFinished(runnable, System.currentTimeMillis());
      }
    }
  }

  void reincludeSkippedItems() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    synchronized (LOCK) {
      int size = mySkippedItems.size();
      for (int i = size - 1; i >= 0; i--) {
        RunnableInfo item = mySkippedItems.get(i);
        myQueue.addFirst(item);
      }
      // .clear() may be expensive
      if (size < 20) {
        mySkippedItems.clear();
      }
      else {
        mySkippedItems = new ArrayList<>();
      }
      requestFlush();
    }
  }

  void purgeExpiredItems() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    synchronized (LOCK) {
      reincludeSkippedItems();

      List<RunnableInfo> alive = new ArrayList<>(myQueue.size());
      for (RunnableInfo info : myQueue) {
        if (!info.expired.value(null)) {
          alive.add(info);
        }
      }
      if (alive.size() < myQueue.size()) {
        myQueue.clear();
        myQueue.addAll(alive);
      }
      requestFlush();
    }
  }

  private boolean FLUSHER_SCHEDULED; // guarded by LOCK

  // must be run under LOCK
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

  final static class RunnableInfo {
    @NotNull private final Runnable runnable;
    @NotNull private final ModalityState modalityState;
    @NotNull private final Condition<?> expired;
    @NotNull
    private final String clientId;

    @Async.Schedule
    RunnableInfo(@NotNull Runnable runnable,
                 @NotNull ModalityState modalityState,
                 @NotNull Condition<?> expired) {
      this.runnable = runnable;
      this.modalityState = modalityState;
      this.expired = expired;
      this.clientId = ClientId.getCurrentValue();
    }

    @Override
    @NonNls
    public String toString() {
      return "[runnable: " + runnable + "; state=" + modalityState + (expired.value(null) ? "; expired" : "")+"] ";
    }
  }
}
