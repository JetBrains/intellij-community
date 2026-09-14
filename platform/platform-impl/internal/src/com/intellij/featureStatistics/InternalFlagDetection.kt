// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.featureStatistics

import com.intellij.featureStatistics.fusCollectors.EAPUsageCollector
import com.intellij.ide.plugins.PluginManager
import com.intellij.internal.statistic.collectors.fus.project.isIdeaProject
import com.intellij.internal.statistic.eventLog.validator.storage.persistence.EventLogMetadataSettingsPersistence
import com.intellij.internal.statistic.utils.StatisticsUploadAssistant
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

internal class InternalFlagDetection : ProjectActivity {
  override suspend fun execute(project: Project) {
    val metadataSettingsPersistence = serviceAsync<EventLogMetadataSettingsPersistence>()
    if (metadataSettingsPersistence.isInternal) return

    val isMonorepo = isIdeaProject(project)

    val isLicensedToJetBrains = EAPUsageCollector.isJBTeam()

    val internalPluginIds = setOf(
      "com.jetbrains.intellij.api.watcher",
      "com.jetbrains.idea.safepush",
      "com.intellij.internalTools"
    )

    // detect plugins
    val internalPluginsDetected = internalPluginIds.any { pluginId ->
      PluginManager.isPluginInstalled(PluginId.getId(pluginId))
    }

    val fusTest = StatisticsUploadAssistant.isTestStatisticsEnabled()

    // store
    metadataSettingsPersistence.isInternal = isMonorepo || isLicensedToJetBrains || internalPluginsDetected || fusTest
  }
}