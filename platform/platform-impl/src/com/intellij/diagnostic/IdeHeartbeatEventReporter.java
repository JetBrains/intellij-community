// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic;

import com.intellij.internal.statistic.eventLog.EventLogGroup;
import com.intellij.internal.statistic.eventLog.events.*;
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.sun.management.OperatingSystemMXBean;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public final class IdeHeartbeatEventReporter implements Disposable {
  static final int UI_RESPONSE_LOGGING_INTERVAL_MS = 100_000;

  @Nullable
  private final ScheduledExecutorService myExecutor;
  @Nullable
  private final ScheduledFuture<?> myThread;

  private long myLastCpuTime = -1;
  private long myLastGcTime = -1;
  private final List<GarbageCollectorMXBean> myGcBeans = ManagementFactory.getGarbageCollectorMXBeans();

  IdeHeartbeatEventReporter() {
    myExecutor = AppExecutorUtil.createBoundedScheduledExecutorService("IDE Heartbeat", 1);
    myThread = myExecutor.scheduleWithFixedDelay(
      this::recordHeartbeat,
      Registry.intValue("ide.heartbeat.delay") /* don't execute during start-up */, UI_RESPONSE_LOGGING_INTERVAL_MS, TimeUnit.MILLISECONDS
    );
  }

  private void recordHeartbeat() {
    OperatingSystemMXBean mxBean = (OperatingSystemMXBean)ManagementFactory.getOperatingSystemMXBean();

    int systemCpuLoad = (int)Math.round(mxBean.getSystemCpuLoad() * 100);
    systemCpuLoad = systemCpuLoad >= 0 ? systemCpuLoad : -1;

    double swapSize = mxBean.getTotalSwapSpaceSize();
    int swapLoad = swapSize > 0 ? (int)((1 - mxBean.getFreeSwapSpaceSize() / swapSize) * 100) : 0;

    long totalGcTime = myGcBeans.stream().mapToLong(GarbageCollectorMXBean::getCollectionTime).sum();
    long thisGcTime = myLastGcTime == -1 ? 0 : totalGcTime - myLastGcTime;
    myLastGcTime = thisGcTime;

    long thisCpuTime;
    long totalCpuTime = mxBean.getProcessCpuTime();
    if (totalCpuTime < 0) {
      thisCpuTime = -1;
    }
    else {
      thisCpuTime = totalCpuTime - myLastCpuTime;
      myLastCpuTime = thisCpuTime;
    }

    // don't report total GC time in the first 5 minutes of IJ execution
    UILatencyLogger.HEARTBEAT.log(
      UILatencyLogger.SYSTEM_CPU_LOAD.with(systemCpuLoad),
      UILatencyLogger.SWAP_LOAD.with(swapLoad),
      UILatencyLogger.CPU_TIME.with((int) TimeUnit.NANOSECONDS.toMillis(thisCpuTime)),
      UILatencyLogger.GC_TIME.with((int) thisGcTime));
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

  final static class Loader implements StartupActivity, StartupActivity.DumbAware {
    @Override
    public void runActivity(@NotNull Project project) {
      ApplicationManager.getApplication().getService(IdeHeartbeatEventReporter.class);
    }
  }

  public static final class UILatencyLogger extends CounterUsagesCollector {
    private static final EventLogGroup GROUP = new EventLogGroup("performance", 65);

    private static final IntEventField SYSTEM_CPU_LOAD = EventFields.Int("system_cpu_load");
    private static final IntEventField SWAP_LOAD = EventFields.Int("swap_load");
    private static final IntEventField CPU_TIME = EventFields.Int("cpu_time_ms");
    private static final IntEventField GC_TIME = EventFields.Int("gc_time_ms");
    private static final VarargEventId HEARTBEAT = GROUP.registerVarargEvent(
      "heartbeat",
      SYSTEM_CPU_LOAD,
      SWAP_LOAD,
      CPU_TIME,
      GC_TIME);
    public static final EventId1<Long> LATENCY = GROUP.registerEvent("ui.latency", EventFields.DurationMs);
    public static final EventId1<Long> LAGGING = GROUP.registerEvent("ui.lagging", EventFields.DurationMs);
    public static final BooleanEventField COLD_START = EventFields.Boolean("cold_start");
    public static final VarargEventId POPUP_LATENCY = GROUP.registerVarargEvent("popup.latency",
                                                                                EventFields.DurationMs,
                                                                                EventFields.ActionPlace,
                                                                                COLD_START,
                                                                                EventFields.Language);

    @Override
    public EventLogGroup getGroup() {
      return GROUP;
    }
  }
}
