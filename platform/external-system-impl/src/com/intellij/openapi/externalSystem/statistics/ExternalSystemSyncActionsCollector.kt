// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.statistics

import com.intellij.featureStatistics.fusCollectors.EventsRateThrottle
import com.intellij.featureStatistics.fusCollectors.ThrowableDescription
import com.intellij.ide.plugins.PluginUtil
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventFields.Boolean
import com.intellij.internal.statistic.eventLog.events.EventFields.DurationMs
import com.intellij.internal.statistic.eventLog.events.EventFields.Int
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.internal.statistic.utils.getPluginInfoById
import com.intellij.internal.statistic.utils.platformPlugin
import com.intellij.openapi.externalSystem.model.ExternalSystemDataKeys
import com.intellij.openapi.externalSystem.model.ExternalSystemException
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
enum class Phase { GRADLE_CALL, PROJECT_RESOLVERS, DATA_SERVICES, WORKSPACE_MODEL_APPLY }

/**
 * Collect gradle import stats.
 *
 * This collector is an internal implementation aimed gather data on
 * the Gradle synchronization duration. Phases are also
 * specific to Gradle build system.
 */
@ApiStatus.Internal
object ExternalSystemSyncActionsCollector : CounterUsagesCollector() {
  override fun getGroup(): EventLogGroup = GROUP

  val GROUP = EventLogGroup("build.gradle.import", 10)

  private val activityIdField = EventFields.Long("ide_activity_id")
  private val importPhaseField = EventFields.Enum<Phase>("phase")

  val syncStartedEvent = GROUP.registerEvent("gradle.sync.started", activityIdField)

  private val isParallelModelFetch = Boolean("parallel_model_fetch")
  private val isSyncSuccessful = Boolean("sync_successful")
  private val isFirstSyncWithIdeCaches = Boolean("first_sync_with_ide_caches")

  val syncFinishedEvent = GROUP.registerVarargEvent("gradle.sync.finished", activityIdField, DurationMs,
                                                    isParallelModelFetch, isSyncSuccessful, isFirstSyncWithIdeCaches)
  private val phaseStartedEvent = GROUP.registerEvent("phase.started", activityIdField, importPhaseField)


  private val errorField = EventFields.Class("error")
  private val severityField = EventFields.String("severity", listOf("fatal", "warning"))
  private val errorHashField = Int("error_hash")
  private val errorCountField = Int("error_count")
  private val tooManyErrorsField = Boolean("too_many_errors")

  private val errorEvent = GROUP.registerVarargEvent("error",
                                                     activityIdField,
                                                     errorField,
                                                     severityField,
                                                     errorHashField,
                                                     EventFields.PluginInfo,
                                                     tooManyErrorsField)

  val phaseFinishedEvent = GROUP.registerVarargEvent("phase.finished",
                                                     activityIdField,
                                                     importPhaseField,
                                                     DurationMs,
                                                     errorCountField)

  private val ourErrorsRateThrottle = EventsRateThrottle(100, 5L * 60 * 1000) // 100 errors per 5 minutes

  private class GradleSyncDetails(
    val timestamp: Long,
    val parallelModelFetchEnabled: Boolean,
    val isFirstSyncWithIdeCaches: Boolean?,
  )

  private const val tsCacheCapasity = 100
  private val idToStartTS = object: LinkedHashMap<Long, GradleSyncDetails>(tsCacheCapasity) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, GradleSyncDetails>?): Boolean {
      return size > tsCacheCapasity
    }
  }

  @JvmStatic
  fun logSyncStarted(project: Project?, activityId: Long, parallelModelFetchEnabled: Boolean) {
    idToStartTS[activityId] = GradleSyncDetails(
      System.currentTimeMillis(),
      parallelModelFetchEnabled,
      project?.getUserData(ExternalSystemDataKeys.NEWLY_OPENED_PROJECT_WITH_IDE_CACHES)
    )
    syncStartedEvent.log(project, activityId)
  }

  @JvmStatic
  fun logSyncFinished(project: Project?, activityId: Long, success: Boolean) {
    val nowTS = System.currentTimeMillis()
    syncFinishedEvent.log(project) {
      add(activityIdField with activityId)
      val syncDetails = idToStartTS[activityId]
      if (syncDetails != null) {
        add(DurationMs with nowTS - syncDetails.timestamp)
        add(isParallelModelFetch with syncDetails.parallelModelFetchEnabled)
        if (syncDetails.isFirstSyncWithIdeCaches != null) {
          add(isFirstSyncWithIdeCaches with syncDetails.isFirstSyncWithIdeCaches)
        }
      }
      else {
        add(DurationMs with -1)
        add(isParallelModelFetch with false)
      }
      add(isSyncSuccessful with success)
    }
  }

  @JvmStatic
  fun logPhaseStarted(project: Project?, activityId: Long, phase: Phase) = phaseStartedEvent.log(project, activityId, phase)

  @JvmStatic
  @JvmOverloads
  fun logPhaseFinished(project: Project?, activityId: Long, phase: Phase, durationMs: Long, errorCount: Int = 0) =
    phaseFinishedEvent.log(project, activityIdField.with(activityId), importPhaseField.with(phase), DurationMs.with(durationMs),
                           EventPair(errorCountField, errorCount))

  @JvmStatic
  fun logError(project: Project?, activityId: Long, throwable: Throwable) {
    val description = ThrowableDescription(throwable)
    val framesHash = if (throwable is ExternalSystemException) {
      throwable.originalReason.hashCode()
    }
    else {
      description.getLastFrames(50).hashCode()
    }
    val data = ArrayList<EventPair<*>>()
    data.add(activityIdField.with(activityId))
    data.add(severityField.with("fatal"))

    val pluginId = PluginUtil.getInstance().findPluginId(throwable)
    data.add(EventFields.PluginInfo.with(if (pluginId == null) platformPlugin else getPluginInfoById(pluginId)))
    data.add(errorField.with(description.throwableClass))
    if (ourErrorsRateThrottle.tryPass(System.currentTimeMillis())) {
      data.add(errorHashField.with(framesHash))
    }
    else {
      data.add(tooManyErrorsField.with(true))
    }

    errorEvent.log(project, *data.toTypedArray())
  }
}
