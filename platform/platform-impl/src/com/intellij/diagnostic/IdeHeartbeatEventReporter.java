// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic;

import com.intellij.featureStatistics.fusCollectors.LifecycleUsageTriggerCollector;
import com.intellij.internal.DebugAttachDetector;
import com.intellij.internal.statistic.eventLog.EventLogGroup;
import com.intellij.internal.statistic.eventLog.events.EventFields;
import com.intellij.internal.statistic.eventLog.events.EventId1;
import com.intellij.internal.statistic.eventLog.events.EventId2;
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.sun.management.OperatingSystemMXBean;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public final class IdeHeartbeatEventReporter implements Disposable {
  private static final int UI_RESPONSE_LOGGING_INTERVAL_MS = 100_000;
  private static final int TOLERABLE_UI_LATENCY = 100;

  @Nullable
  private final ScheduledExecutorService myExecutor;
  @Nullable
  private final ScheduledFuture<?> myThread;

  private volatile long myPreviousLoggedUIResponse = 0;

  IdeHeartbeatEventReporter() {
    ApplicationManager.getApplication().getMessageBus().connect(this)
      .subscribe(IdePerformanceListener.TOPIC, new IdePerformanceListener() {
        final boolean isDebugEnabled = DebugAttachDetector.isDebugEnabled();

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

    if (ApplicationManager.getApplication().isEAP()) {
      myExecutor = AppExecutorUtil.createBoundedScheduledExecutorService("IDE Heartbeat", 1);
      myThread = myExecutor.scheduleWithFixedDelay(
        IdeHeartbeatEventReporter::recordHeartbeat,
        TimeUnit.MINUTES.toMillis(5) /* don't execute during start-up */, UI_RESPONSE_LOGGING_INTERVAL_MS, TimeUnit.MILLISECONDS
      );
    }
    else {
      myExecutor = null;
      myThread = null;
    }
  }

  private static void recordHeartbeat() {
    OperatingSystemMXBean mxBean = (OperatingSystemMXBean)ManagementFactory.getOperatingSystemMXBean();

    int systemCpuLoad = (int)Math.round(mxBean.getSystemCpuLoad() * 100);
    systemCpuLoad = systemCpuLoad >= 0 ? systemCpuLoad : -1;

    double swapSize = mxBean.getTotalSwapSpaceSize();
    int swapLoad = swapSize > 0 ? (int)((1 - mxBean.getFreeSwapSpaceSize() / swapSize) * 100) : 0;

    UILatencyLogger.HEARTBEAT.log(systemCpuLoad, swapLoad);
  }

  @Override
  public void dispose() {
    if (myThread != null) {
      myThread.cancel(true);
    }
    if (myExecutor != null) {
      myExecutor.shutdownNow();
    }
  }

  public static final class UILatencyLogger extends CounterUsagesCollector {
    private static final EventLogGroup GROUP = new EventLogGroup("performance", 61);

    private static final EventId2<Integer, Integer> HEARTBEAT = GROUP.registerEvent(
      "heartbeat", EventFields.Int("system_cpu_load"), EventFields.Int("swap_load"));
    private static final EventId1<Long> LATENCY = GROUP.registerEvent("ui.latency", EventFields.DurationMs);
    private static final EventId1<Long> LAGGING = GROUP.registerEvent("ui.lagging", EventFields.DurationMs);
    public static final EventId2<Long, String> POPUP_LATENCY =
      GROUP.registerEvent("popup.latency", EventFields.DurationMs, EventFields.ActionPlace);

    @Override
    public EventLogGroup getGroup() {
      return GROUP;
    }
  }
}
