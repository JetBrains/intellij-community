// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.statistics

import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector
import com.intellij.openapi.externalSystem.ExternalSystemModulePropertyManager
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemProjectTrackerSettings
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import java.util.*

class ExternalSystemSettingsCollector : ProjectUsagesCollector() {
  override fun getGroup(): EventLogGroup {
    return GROUP
  }

  override fun getMetrics(project: Project): Set<MetricEvent> {
    val usages = mutableSetOf<MetricEvent>()

    val trackerSettings = ExternalSystemProjectTrackerSettings.getInstance(project)
    usages.add(AUTO_RELOAD_TYPE.metric(trackerSettings.autoReloadType))

    usages.add(HAS_SHARED_SOURCES.metric(HasSharedSourcesUtil.hasSharedSources(project)))

    for (manager in ExternalSystemApiUtil.getAllManagers()) {
      val systemId = getAnonymizedSystemId(manager.systemId)

      val projects = manager.settingsProvider.`fun`(project).linkedProjectsSettings

      usages.add(NUMBER_OF_LINKED_PROJECT.metric(projects.size, systemId))

      for (projectsSetting in projects) {
        usages.add(USE_QUALIFIED_MODULE_NAMES.metric(projectsSetting.isUseQualifiedModuleNames, systemId))
        usages.add(MODULES_COUNT.metric(projectsSetting.modules.size, systemId))
      }
    }

    val mavenModules = ModuleManager.getInstance(project).modules.count {
      ExternalSystemModulePropertyManager.getInstance(it).isMavenized()
    }
    if (mavenModules > 0) {
      usages.add(MODULES_COUNT.metric(mavenModules, "Maven"))
    }

    return usages
  }

  companion object {
    private val GROUP = EventLogGroup("build.tools.state", 5)
    private val AUTO_RELOAD_TYPE = GROUP.registerEvent("autoReloadType",
                                                       EventFields.Enum("value",
                                                                        ExternalSystemProjectTrackerSettings.AutoReloadType::class.java) {
                                                         it.name.lowercase(Locale.ENGLISH)
                                                       })
    private val EXTERNAL_SYSTEM_ID = EventFields.StringValidatedByEnum("externalSystemId", "build_tools")
    private val NUMBER_OF_LINKED_PROJECT = GROUP.registerEvent("numberOfLinkedProject", EventFields.Count, EXTERNAL_SYSTEM_ID)
    private val USE_QUALIFIED_MODULE_NAMES = GROUP.registerEvent("useQualifiedModuleNames", EventFields.Enabled, EXTERNAL_SYSTEM_ID)
    private val MODULES_COUNT = GROUP.registerEvent("modules.count", EventFields.RoundedInt("count_rounded"), EXTERNAL_SYSTEM_ID)
    private val HAS_SHARED_SOURCES = GROUP.registerEvent("hasSharedSources", EventFields.Enabled)
  }
}
