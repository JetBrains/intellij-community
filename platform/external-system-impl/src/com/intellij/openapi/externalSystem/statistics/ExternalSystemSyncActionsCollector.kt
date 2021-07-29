// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.statistics

import com.intellij.featureStatistics.fusCollectors.EventsIdentityThrottle
import com.intellij.featureStatistics.fusCollectors.EventsRateThrottle
import com.intellij.featureStatistics.fusCollectors.ThrowableDescription
import com.intellij.ide.plugins.PluginUtil
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventField
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventFields.Boolean
import com.intellij.internal.statistic.eventLog.events.EventFields.DurationMs
import com.intellij.internal.statistic.eventLog.events.EventFields.Int
import com.intellij.internal.statistic.eventLog.events.EventFields.StringListValidatedByCustomRule
import com.intellij.internal.statistic.eventLog.events.EventFields.StringValidatedByCustomRule
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.internal.statistic.utils.getPluginInfoById
import com.intellij.internal.statistic.utils.platformPlugin
import com.intellij.openapi.project.Project

enum class Phase { GRADLE_CALL, PROJECT_RESOLVERS, DATA_SERVICES }

class ExternalSystemSyncActionsCollector : CounterUsagesCollector() {
  override fun getGroup(): EventLogGroup = GROUP

  companion object {
    private val GROUP = EventLogGroup("build.gradle.import", 1)

    val activityIdField = EventFields.Long("ide_activity_id")
    val importPhaseField = EventFields.Enum<Phase>("phase")

    private val syncStartedEvent = GROUP.registerEvent("gradle.sync.started", activityIdField)
    private val syncFinishedEvent = GROUP.registerEvent("gradle.sync.finished", activityIdField, Boolean("sync_successful"))
    private val phaseStartedEvent = GROUP.registerEvent("phase.started", activityIdField, importPhaseField)
    private val phaseFinishedEvent = GROUP.registerVarargEvent("phase.finished",
                                                               activityIdField,
                                                               importPhaseField,
                                                               DurationMs,
                                                               Int("error_count"))

    val errorField = StringValidatedByCustomRule("error", "class_name")
    val severityField = EventFields.String("severity", listOf("fatal", "warning"))
    val errorHashField = Int("error_hash")
    val tooManyErrorsField = Boolean("too_many_errors")
    val errorFramesField = StringListValidatedByCustomRule("error_frames", "method_name")
    val errorSizeField: EventField<Int> = Int("error_size")

    private val errorEvent = GROUP.registerVarargEvent("error",
                                                       activityIdField,
                                                       errorField,
                                                       severityField,
                                                       errorHashField,
                                                       EventFields.PluginInfo,
                                                       tooManyErrorsField,
                                                       errorFramesField,
                                                       errorSizeField)

    private val ourErrorsRateThrottle = EventsRateThrottle(100, 5L * 60 * 1000) // 100 errors per 5 minutes
    private val ourErrorsIdentityThrottle = EventsIdentityThrottle(50, 60L * 60 * 1000) // 1 unique error per 1 hour

    fun logSyncStarted(project: Project?, activityId: Long) =  syncStartedEvent.log(project, activityId)
    fun logSyncFinished(project: Project?, activityId: Long, success: Boolean) =  syncFinishedEvent.log(project, activityId, success)

    fun logPhaseStarted(project: Project?, activityId: Long, phase: Phase) = phaseStartedEvent.log(project, activityId, phase)
    fun logPhaseFinished(project: Project?, activityId: Long, phase: Phase, durationMs: Long) =
      phaseFinishedEvent.log(project, activityIdField.with(activityId), importPhaseField.with(phase), DurationMs.with(durationMs))

    fun logError(project: Project?, activityId: Long, throwable: Throwable) {
      val description = ThrowableDescription(throwable)
      val data: MutableList<EventPair<*>> = ArrayList()
      data.add(activityIdField.with(activityId))

      val pluginId = PluginUtil.getInstance().findPluginId(throwable)
      data.add(EventFields.PluginInfo.with(if (pluginId == null) platformPlugin else getPluginInfoById(pluginId)))
      data.add(errorField.with(description.className))
      if (ourErrorsRateThrottle.tryPass(System.currentTimeMillis())) {
        val frames = description.getLastFrames(50)
        val framesHash = frames.hashCode()
        data.add(errorHashField.with(framesHash))
        if (ourErrorsIdentityThrottle.tryPass(framesHash, System.currentTimeMillis())) {
          data.add(errorFramesField.with(frames))
          data.add(errorSizeField.with(description.size))
        }
      }
      else {
        data.add(tooManyErrorsField.with(true))
      }

      errorEvent.log(project, *data.toTypedArray())
    }
  }
}
