// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.statistics

import com.intellij.internal.statistic.IdeActivity
import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.beans.newMetric
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventFields.StringValidatedByCustomRule
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector
import com.intellij.openapi.externalSystem.ExternalSystemModulePropertyManager
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil
import com.intellij.openapi.externalSystem.service.execution.TargetEnvironmentConfigurationProvider
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Version

class ExternalSystemUsagesCollector : ProjectUsagesCollector() {
  override fun getGroupId() = "build.tools"

  override fun getMetrics(project: Project): Set<MetricEvent> {
    val usages = mutableSetOf<MetricEvent>()
    for (manager in ExternalSystemApiUtil.getAllManagers()) {
      if (!manager.getSettingsProvider().`fun`(project).getLinkedProjectsSettings().isEmpty()) {
        usages.add(newMetric("externalSystemId", getAnonymizedSystemId(manager.getSystemId())))
      }
    }

    ModuleManager.getInstance(project).modules.find { ExternalSystemModulePropertyManager.getInstance(it).isMavenized() }?.let {
      usages.add(newMetric("externalSystemId", "Maven"))
    }
    return usages
  }

  enum class ExternalSystemTaskId {
    ResolveProject,
    ExecuteTask,
  }

  companion object {
    private val TASK_ID_FIELD = EventFields.Enum<ExternalSystemTaskId>("task_id")
    private val TARGET_FIELD = StringValidatedByCustomRule("target", "run_target")

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
                                  environmentConfigurationProvider: TargetEnvironmentConfigurationProvider?): IdeActivity {
      return IdeActivity(project, "external.project.task").startedWithData { data ->
        addExternalSystemId(data, systemId);
        EventPair(TASK_ID_FIELD, taskId).addData(data)
        environmentConfigurationProvider?.environmentConfiguration?.typeId?.also {
          EventPair(TARGET_FIELD, it).addData(data)
        }
      }
    }
  }
}
