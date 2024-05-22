// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic

import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.fus.MachineIdManager
import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector

/**
 * Provides mapping of machine ids between 2 recorders
 */
class IJFUSMapper: ApplicationUsagesCollector() {
  private val GROUP: EventLogGroup = EventLogGroup("map.ml.fus", 1, IJMapperEventLoggerProvider.RECORDER_ID)

  private val mlMachineId = EventFields.StringValidatedByRegexpReference("ml_machine_id", "hash")
  private val fusMachineId = EventFields.StringValidatedByRegexpReference("fus_machine_id", "hash")

  private val report = GROUP.registerEvent("paired", mlMachineId, fusMachineId, "Paired FUS and ML machine_id")

  override fun getMetrics(): Set<MetricEvent> {
    return setOf(report.metric(
      MachineIdManager.getAnonymizedMachineId("JetBrainsML", ""),
      MachineIdManager.getAnonymizedMachineId("JetBrainsFUS", ""),
    ))
  }
}