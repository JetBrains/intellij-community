// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.featureStatistics

import com.intellij.featureStatistics.fusCollectors.EAPUsageCollector
import com.intellij.ide.plugins.PluginManager
import com.intellij.internal.statistic.collectors.fus.project.isIdeaProject
import com.intellij.internal.statistic.eventLog.validator.storage.persistence.EventLogMetadataSettingsPersistence
import com.intellij.internal.statistic.utils.StatisticsUploadAssistant
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

private class InternalFlagDetection : ProjectActivity {
  private val internalPluginIds = setOf(
    "com.jetbrains.intellij.api.watcher",
    "com.jetbrains.idea.safepush",
    "com.intellij.internalTools",
    "com.intellij.sisyphus", // exception analyzer
  )

  override suspend fun execute(project: Project) {
    if (EventLogMetadataSettingsPersistence.getInstance().isInternal) return

    val isMonorepo = isIdeaProject(project)

    val isLicensedToJetBrains = EAPUsageCollector.isJBTeam()

    // detect plugins
    val internalPluginsDetected = internalPluginIds.any { pluginId ->
      PluginManager.isPluginInstalled(PluginId.getId(pluginId))
    }

    val fusTest = StatisticsUploadAssistant.isTestStatisticsEnabled()

    // store
    EventLogMetadataSettingsPersistence.getInstance().isInternal = isMonorepo || isLicensedToJetBrains || internalPluginsDetected || fusTest
  }
}