// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.cds

import com.intellij.internal.statistic.eventLog.FeatureUsageData
import com.intellij.internal.statistic.service.fus.collectors.FUCounterUsageLogger
import java.lang.management.ManagementFactory

object CDSFUSCollector {
  private const val groupId = "intellij.cds"

  private val uptime
    get() = System.currentTimeMillis() - ManagementFactory.getRuntimeMXBean().uptime

  fun logCDSStatus(enabled: Boolean,
                   runningWithArchive: Boolean) {
    FUCounterUsageLogger.getInstance().logEvent(groupId, "running.with.cds", FeatureUsageData()
      .addData("running_with_archive", runningWithArchive)
      .addData("status", if (enabled) "enabled" else "disabled")
    )
  }

  fun logCDSBuildingStarted() {
    FUCounterUsageLogger.getInstance()
      .logEvent(groupId,
                "building.cds.started",
                FeatureUsageData().addData("uptime_millis", uptime)
      )
  }

  fun logCDSBuildingCompleted(duration: Long, result: CDSTaskResult) {
    FUCounterUsageLogger.getInstance()
      .logEvent(groupId,
                "building.cds.finished",
                FeatureUsageData()
                  .addData("duration", duration)
                  .addData("uptime_millis", uptime)
                  .addData("status", result.statusName)
      )
  }
}
