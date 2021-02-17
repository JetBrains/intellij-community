// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic;

import com.intellij.featureStatistics.fusCollectors.LifecycleUsageTriggerCollector;
import com.intellij.internal.DebugAttachDetector;
import com.intellij.internal.statistic.eventLog.EventLogGroup;
import com.intellij.internal.statistic.eventLog.events.EventFields;
import com.intellij.internal.statistic.eventLog.events.EventId1;
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.Nullable;

import java.io.File;

final class IdeHeartbeatEventReporter implements Disposable {
  private static final int UI_RESPONSE_LOGGING_INTERVAL_MS = 100_000;
  private static final int TOLERABLE_UI_LATENCY = 100;

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
  }


  @Override
  public void dispose() {

  }

  public static final class UILatencyLogger extends CounterUsagesCollector {
    private static final EventLogGroup GROUP = new EventLogGroup("performance", 57);

    private static final EventId1<Long> LATENCY = GROUP.registerEvent("ui.latency", EventFields.DurationMs);
    private static final EventId1<Long> LAGGING = GROUP.registerEvent("ui.lagging", EventFields.DurationMs);

    @Override
    public EventLogGroup getGroup() {
      return GROUP;
    }
  }
}
