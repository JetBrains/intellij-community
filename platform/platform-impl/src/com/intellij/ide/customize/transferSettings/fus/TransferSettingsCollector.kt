// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.customize.transferSettings.fus

import com.intellij.ide.customize.transferSettings.*
import com.intellij.ide.customize.transferSettings.models.FailedIdeVersion
import com.intellij.ide.customize.transferSettings.models.IdeVersion
import com.intellij.ide.customize.transferSettings.models.PatchedKeymap
import com.intellij.ide.customize.transferSettings.models.Settings
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.runAndLogException
import kotlin.time.Duration

object TransferSettingsCollector : CounterUsagesCollector() {

  private val logger = logger<TransferSettingsCollector>()

  private val GROUP = EventLogGroup("wizard.transfer.settings", 2)
  override fun getGroup(): EventLogGroup = GROUP

  private val ideField = EventFields.Enum<TransferableIdeId>("ide")
  private val ideVersionField = EventFields.NullableEnum<TransferableIdeVersionId>("version")
  private val featureField = EventFields.Enum<TransferableIdeFeatureId>("feature")
  private val performanceMetricTypeTypeField = EventFields.Enum<PerformanceMetricType>("type")
  private val perfEventValueField = EventFields.Long("value")

  // Common events
  private val transferSettingsShown = GROUP.registerEvent("transfer.settings.shown")
  private val transferSettingsSkipped = GROUP.registerEvent("transfer.settings.skipped")
  private val importStarted = GROUP.registerEvent("import.started")
  private val importSucceeded = GROUP.registerEvent("import.succeeded", ideField)
  private val importFailed = GROUP.registerEvent("import.failed", ideField)

  // Discovery events
  private val instancesOfIdeFound = GROUP.registerEvent(
    "instances.of.ide.found",
    ideField,
    ideVersionField,
    EventFields.Count
  )
  private val instancesOfIdeFailed = GROUP.registerEvent(
    "instances.of.ide.failed",
    ideField,
    EventFields.Count
  )
  private val featureDetected = GROUP.registerEvent("feature.detected", ideField, featureField)
  private val recentProjectsDetected = GROUP.registerEvent("recent.projects.detected", ideField, EventFields.Count)

  // Import events
  private val lafImported = GROUP.registerEvent("laf.imported", EventFields.Enum<TransferableLafId>("laf"))
  private val shortcutsTransferred = GROUP.registerEvent(
    "shortcuts.transferred",
    EventFields.Enum<TransferableKeymapId>("keymap"),
    EventFields.Int("added_shortcut_count"),
    EventFields.Int("removed_shortcut_count")
  )
  private val recentProjectsTransferred = GROUP.registerEvent("recent.projects.transferred", ideField, EventFields.Count)
  private val featureImported = GROUP.registerEvent("feature.imported", featureField, ideField)

  // Performance events, see RIDER-60328.
  enum class PerformanceMetricType {
    SubName, Registry, ReadSettingsFile, Total
  }

  private val performanceMeasuredEvent = GROUP.registerVarargEvent(
    "performance.measured",
    performanceMetricTypeTypeField,
    ideField,
    ideVersionField,
    perfEventValueField
  )

  fun logTransferSettingsShown() {
    transferSettingsShown.log()
  }

  @Suppress("unused") // Used in Rider
  fun logTransferSettingsSkipped() {
    transferSettingsSkipped.log()
  }

  fun logImportStarted() {
    importStarted.log()
  }

  fun logImportSucceeded(ideVersion: IdeVersion, settings: Settings) {
    logger.runAndLogException {
      val ide = ideVersion.transferableId
      importSucceeded.log(ide)
      settings.laf?.transferableId?.let { lafImported.log(it) }
      settings.keymap?.let { keymap ->
        val patchedKeymap = keymap as? PatchedKeymap
        shortcutsTransferred.log(
          keymap.transferableId,
          patchedKeymap?.overrides?.size ?: 0,
          patchedKeymap?.removal?.size ?: 0
        )
      }
      recentProjectsTransferred.log(ide, settings.recentProjects.size)
      for (plugin in settings.plugins) {
        featureImported.log(plugin.transferableId, ide)
      }
    }
  }

  fun logImportFailed(ideVersion: IdeVersion) {
    logger.runAndLogException {
      importFailed.log(ideVersion.transferableId)
    }
  }

  fun logIdeVersionsFound(versions: List<IdeVersion>) {
    logger.runAndLogException {
      versions
        .groupBy { it.transferableId to it.transferableVersion }
        .forEach { (id, version), instances ->
          instancesOfIdeFound.log(id, version, instances.size)
        }
    }
  }

  fun logIdeVersionsFailed(versions: List<FailedIdeVersion>) {
    logger.runAndLogException {
      versions
        .groupBy { it.transferableId }
        .forEach { (id, instances) ->
          instancesOfIdeFailed.log(id, instances.size)
        }
    }
  }

  fun logIdeSettingsDiscovered(ideVersion: IdeVersion, settings: Settings) {
    logger.runAndLogException {
      val ide = ideVersion.transferableId
      for (plugin in settings.plugins) {
        featureDetected.log(ide, plugin.transferableId)
      }
      recentProjectsDetected.log(ide, settings.recentProjects.size)
    }
  }

  fun logPerformanceMeasured(type: PerformanceMetricType, ide: TransferableIdeId, version: TransferableIdeVersionId?, duration: Duration) {
    logger.runAndLogException {
      performanceMeasuredEvent.log(
        EventPair(performanceMetricTypeTypeField, type),
        EventPair(ideField, ide),
        EventPair(ideVersionField, version),
        EventPair(perfEventValueField, duration.inWholeMilliseconds)
      )
    }
  }
}