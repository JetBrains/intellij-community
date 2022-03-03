// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.statistics

import com.intellij.internal.statistic.StructuredIdeActivity
import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.beans.newMetric
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector
import com.intellij.openapi.externalSystem.ExternalSystemModulePropertyManager
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil
import com.intellij.openapi.externalSystem.service.execution.TargetEnvironmentConfigurationProvider
import com.intellij.openapi.externalSystem.statistics.ExternalSystemTaskCollector.Companion.EXTERNAL_TASK_ACTIVITY
import com.intellij.openapi.externalSystem.statistics.ExternalSystemTaskCollector.Companion.TARGET_FIELD
import com.intellij.openapi.externalSystem.statistics.ExternalSystemTaskCollector.Companion.TASK_ID_FIELD
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Version

class ExternalSystemUsagesCollector : ProjectUsagesCollector() {
  override fun getGroup(): EventLogGroup {
    return GROUP
  }

  override fun getMetrics(project: Project): Set<MetricEvent> {
    val usages = mutableSetOf<MetricEvent>()
    for (manager in ExternalSystemApiUtil.getAllManagers()) {
      if (!manager.settingsProvider.`fun`(project).linkedProjectsSettings.isEmpty()) {
        usages.add(EXTERNAL_SYSTEM_ID.metric(getAnonymizedSystemId(manager.systemId)))
      }
    }

    ModuleManager.getInstance(project).modules.find { ExternalSystemModulePropertyManager.getInstance(it).isMavenized() }?.let {
      usages.add(EXTERNAL_SYSTEM_ID.metric("Maven"))
    }
    return usages
  }

  enum class ExternalSystemTaskId {
    ResolveProject,
    ExecuteTask,
  }

  companion object {
    private val GROUP = EventLogGroup("build.tools", 2)
    private val EXTERNAL_SYSTEM_ID = GROUP.registerEvent("externalSystemId", EventFields.StringValidatedByEnum("value", "build_tools"))
    fun getJRETypeUsage(key: String, jreName: String?): MetricEvent {
      val anonymizedName = when {
        jreName.isNullOrBlank() -> "empty"
        jreName in listOf(ExternalSystemJdkUtil.USE_INTERNAL_JAVA,
                          ExternalSystemJdkUtil.USE_PROJECT_JDK,
                          ExternalSystemJdkUtil.USE_JAVA_HOME) -> jreName
        else -> "custom"
      }
      return newMetric(key, anonymizedName)
    }

    fun getJREVersionUsage(project: Project, key: String, jreName: String?): MetricEvent {
      val jdk = ExternalSystemJdkUtil.getJdk(project, jreName)
      val versionString =
        jdk?.versionString?.let { Version.parseVersion(it)?.let { parsed -> "${parsed.major}.${parsed.minor}" } }
        ?: "unknown"

      return newMetric(key, versionString)
    }

    @JvmStatic
    fun externalSystemTaskStarted(project: Project?,
                                  systemId: ProjectSystemId?,
                                  taskId: ExternalSystemTaskId,
                                  environmentConfigurationProvider: TargetEnvironmentConfigurationProvider?): StructuredIdeActivity {
      return EXTERNAL_TASK_ACTIVITY.started(project) {
        val data: MutableList<EventPair<*>> = mutableListOf(
          ExternalSystemActionsCollector.EXTERNAL_SYSTEM_ID.with(anonymizeSystemId(systemId)))
        data.add(TASK_ID_FIELD.with(taskId))
        environmentConfigurationProvider?.environmentConfiguration?.typeId?.also {
          data.add(TARGET_FIELD.with(it))
        }
        data
      }
    }
  }
}
