// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.statistics

import com.intellij.debugger.engine.DebugProcess
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector

object DebuggerStatistics : CounterUsagesCollector() {
  override fun getGroup(): EventLogGroup = GROUP

  private val GROUP = EventLogGroup("java.debugger", 1)

  // fields
  private val breakpointTypeField = EventFields.String("type", listOf(
    "java-exception", "java-collection",
    "java-line", "java-field", "java-method",
    "kotlin-line", "kotlin-field", "kotlin-function",
  ))

  private val averageTimeField = EventFields.Double("avg_time_ms")
  private val countField = EventFields.Count
  private val steppingActionField = EventFields.Enum<SteppingAction>("step_action")
  private val languageField = EventFields.Enum<Engine>("language")


  // events
  private val breakpointInstallationOverhead = GROUP.registerEvent("breakpoint.install.overhead",
                                                                   breakpointTypeField, averageTimeField, countField)
  private val breakpointVisitOverhead = GROUP.registerEvent("breakpoint.visit.overhead", averageTimeField, countField)
  private val steppingOverhead = GROUP.registerVarargEvent("stepping.overhead", steppingActionField, languageField, averageTimeField,
                                                           countField)

  @JvmStatic
  fun logProcessStatistics(debugProcess: DebugProcess) {
    val collectedStats = StatisticsStorage.collectAndClearData(debugProcess)

    val installationOverheads = hashMapOf<String, TimeStats>()

    for ((key, stats) in collectedStats) {

      when (key) {
        is BreakpointVisitStatistic -> {
          breakpointVisitOverhead.log(debugProcess.project, stats.averageTime, stats.hits)
        }
        is BreakpointInstallStatistic -> {
          val xBreakpoint = key.breakpoint.xBreakpoint ?: continue
          val breakpointType = xBreakpoint.type.id
          installationOverheads.merge(breakpointType, TimeStats(stats.timeMs, 1), TimeStats::plus)
        }
        is SteppingStatistic -> {
          steppingOverhead.log(debugProcess.project, steppingActionField.with(key.action),
                               languageField.with(key.engine), averageTimeField.with(stats.averageTime),
                               countField.with(stats.hits))
        }
      }
    }

    for ((type, stats) in installationOverheads) {
      breakpointInstallationOverhead.log(debugProcess.project, type, stats.averageTime, stats.hits)
    }
  }
}

private val TimeStats.averageTime: Double get() = timeMs.toDouble() / hits
