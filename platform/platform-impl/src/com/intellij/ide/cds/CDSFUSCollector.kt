// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.cds

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import java.lang.management.ManagementFactory

class CDSFUSCollector : CounterUsagesCollector() {
  override fun getGroup(): EventLogGroup = GROUP

  companion object {
    private val GROUP = EventLogGroup("intellij.cds", 3)

    private val STATUS = EventFields.String("status", listOf("success", "cancelled", "terminated-by-user", "plugins-changed", "failed"))

    private val STARTED = GROUP.registerEvent("building.cds.started")
    private val RUNNING = GROUP.registerEvent("running.with.cds", EventFields.Boolean("status"),
                                              EventFields.Boolean("running_with_archive"))
    private val FINISHED = GROUP.registerEvent("building.cds.finished", EventFields.DurationMs, STATUS)

    fun logCDSStatus(enabled: Boolean, runningWithArchive: Boolean) {
      RUNNING.log(enabled, runningWithArchive)
    }

    fun logCDSBuildingStarted() {
      STARTED.log()
    }

    fun logCDSBuildingCompleted(duration: Long, result: CDSTaskResult) {
      FINISHED.log(duration, result.statusName)
    }
  }
}
