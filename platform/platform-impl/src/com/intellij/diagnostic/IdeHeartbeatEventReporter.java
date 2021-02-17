// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic;

import com.intellij.featureStatistics.fusCollectors.LifecycleUsageTriggerCollector;
import com.intellij.internal.DebugAttachDetector;
import com.intellij.internal.statistic.eventLog.EventLogGroup;
import com.intellij.internal.statistic.eventLog.events.EventFields;
import com.intellij.internal.statistic.eventLog.events.EventId;
import com.intellij.internal.statistic.eventLog.events.EventId1;
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

final class IdeHeartbeatEventReporter implements Disposable {
  private static final int UI_RESPONSE_LOGGING_INTERVAL_MS = 100_000;
  private static final int TOLERABLE_UI_LATENCY = 100;

  private final ScheduledExecutorService myExecutor = AppExecutorUtil.createBoundedScheduledExecutorService("IDE Heartbeat", 1);
  private final ScheduledFuture<?> myThread;

  private volatile long myPreviousLoggedUIResponse = 0;

  IdeHeartbeatEventReporter() {
    boolean isDebugEnabled = DebugAttachDetector.isDebugEnabled();
    ApplicationManager.getApplication().getMessageBus().connect(this)
      .subscribe(IdePerformanceListener.TOPIC, new IdePerformanceListener() {
        @Override
        public void uiFreezeFinished(long durationMs, @Nullable File reportDir) {
          if (!isDebugEnabled) {
            LifecycleUsageTriggerCollector.onFreeze(durationMs);
          }
        }

        @Override
        public void uiResponded(long latencyMs) {
          final long currentTime = System.currentTimeMillis();
          if (currentTime - myPreviousLoggedUIResponse >= UI_RESPONSE_LOGGING_INTERVAL_MS) {
            myPreviousLoggedUIResponse = currentTime;
            UILatencyLogger.LATENCY.log(latencyMs);
          }
          if (latencyMs >= TOLERABLE_UI_LATENCY && !isDebugEnabled) {
            UILatencyLogger.LAGGING.log(latencyMs);
          }
        }
      });

    myThread = myExecutor.scheduleWithFixedDelay(
      IdeHeartbeatEventReporter::recordHeartbeat,
      UI_RESPONSE_LOGGING_INTERVAL_MS, UI_RESPONSE_LOGGING_INTERVAL_MS, TimeUnit.MILLISECONDS
    );
  }

  private static void recordHeartbeat() {
    UILatencyLogger.HEARTBEAT.log();
  }

  @Override
  public void dispose() {
    if (myThread != null) {
      myThread.cancel(true);
    }
    myExecutor.shutdownNow();
  }

  public static final class UILatencyLogger extends CounterUsagesCollector {
    private static final EventLogGroup GROUP = new EventLogGroup("performance", 58);

    private static final EventId HEARTBEAT = GROUP.registerEvent("heartbeat");
    private static final EventId1<Long> LATENCY = GROUP.registerEvent("ui.latency", EventFields.DurationMs);
    private static final EventId1<Long> LAGGING = GROUP.registerEvent("ui.lagging", EventFields.DurationMs);

    @Override
    public EventLogGroup getGroup() {
      return GROUP;
    }
  }
}
