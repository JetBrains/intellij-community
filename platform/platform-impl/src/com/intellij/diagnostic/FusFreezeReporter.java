// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic;

import com.intellij.featureStatistics.fusCollectors.LifecycleUsageTriggerCollector;
import com.intellij.internal.DebugAttachDetector;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.concurrent.TimeUnit;

final class FusFreezeReporter implements IdePerformanceListener {
  final boolean isDebugEnabled = DebugAttachDetector.isDebugEnabled();
  private volatile long myPreviousLoggedUIResponse = 0;
  private static final int TOLERABLE_UI_LATENCY = 100;

  @Override
  public void uiFreezeFinished(long durationMs, @Nullable File reportDir) {
    if (!isDebugEnabled) {
      LifecycleUsageTriggerCollector.onFreeze(durationMs);
    }
  }

  @Override
  public void uiResponded(long latencyMs) {
    final long currentTime = System.nanoTime();
    final long elapsedMs = TimeUnit.NANOSECONDS.toMillis(currentTime - myPreviousLoggedUIResponse);
    if (elapsedMs >= IdeHeartbeatEventReporter.UI_RESPONSE_LOGGING_INTERVAL_MS) {
      myPreviousLoggedUIResponse = currentTime;
      IdeHeartbeatEventReporter.UILatencyLogger.LATENCY.log(latencyMs);
    }
    if (latencyMs >= TOLERABLE_UI_LATENCY && !isDebugEnabled) {
      IdeHeartbeatEventReporter.UILatencyLogger.LAGGING.log(latencyMs);
    }
  }
}
