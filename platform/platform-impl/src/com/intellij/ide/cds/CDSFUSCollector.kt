// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.cds

import com.intellij.internal.statistic.eventLog.FeatureUsageData
import com.intellij.internal.statistic.service.fus.collectors.FUCounterUsageLogger
import java.lang.management.ManagementFactory

object CDSFUSCollector {
  private const val groupId = "intellij.cds"

  fun logRunningWithCDS() = FUCounterUsageLogger.getInstance().logEvent(groupId, "running.with.cds")

  fun logCDSDisabled() = FUCounterUsageLogger.getInstance().logEvent(groupId, "cds.disabled")
  fun logCDSEnabled() = FUCounterUsageLogger.getInstance().logEvent(groupId, "cds.enabled")

  fun logCDSBuildingStoppedByUser() = FUCounterUsageLogger.getInstance().logEvent(groupId, "building.cds.stopped")
  fun logCDSBuildingInterrupted() = FUCounterUsageLogger.getInstance().logEvent(groupId, "building.cds.interrupted")
  fun logCDSBuildingFailed() = FUCounterUsageLogger.getInstance().logEvent(groupId, "building.cds.failed")
  fun logCDSBuildingStarted() = FUCounterUsageLogger.getInstance().logEvent(groupId, "building.cds.started")

  fun logCDSBuildingCompleted(duration: Long) {
    val processUptime = System.currentTimeMillis() - ManagementFactory.getRuntimeMXBean().uptime
    FUCounterUsageLogger.getInstance()
      .logEvent(groupId,
                "building.cds.completed",
                FeatureUsageData()
                  .addData("duration", duration)
                  .addData("uptime.millis", processUptime)
      )
  }
}
