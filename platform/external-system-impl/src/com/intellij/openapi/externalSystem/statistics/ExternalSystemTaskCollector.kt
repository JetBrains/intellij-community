// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.statistics

import com.intellij.execution.impl.statistics.RunConfigurationUsageTriggerCollector.RunTargetValidator
import com.intellij.internal.statistic.StructuredIdeActivity
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.service.execution.TargetEnvironmentConfigurationProvider
import com.intellij.openapi.externalSystem.statistics.ExternalSystemActionsCollector.EXTERNAL_SYSTEM_ID
import com.intellij.openapi.project.Project

internal object ExternalSystemTaskCollector : CounterUsagesCollector() {
  private val GROUP = EventLogGroup("external.project.task", 3)

  private val TASK_ID_FIELD = EventFields.Enum<ExternalSystemTaskId>("task_id")
  private val TARGET_FIELD = EventFields.StringValidatedByCustomRule("target", RunTargetValidator::class.java)
  private val EXTERNAL_TASK_ACTIVITY = GROUP.registerIdeActivity(null, startEventAdditionalFields = arrayOf(TASK_ID_FIELD,
                                                                                                            TARGET_FIELD,
                                                                                                            EXTERNAL_SYSTEM_ID))

  override fun getGroup(): EventLogGroup = GROUP

  @JvmStatic
  fun externalSystemTaskStarted(project: Project?,
                                systemId: ProjectSystemId?,
                                taskId: ExternalSystemTaskId,
                                environmentConfigurationProvider: TargetEnvironmentConfigurationProvider?): StructuredIdeActivity {
    return EXTERNAL_TASK_ACTIVITY.started(project) {
      val data: MutableList<EventPair<*>> = mutableListOf(EXTERNAL_SYSTEM_ID.with(anonymizeSystemId(systemId)))
      data.add(TASK_ID_FIELD.with(taskId))
      environmentConfigurationProvider?.environmentConfiguration?.typeId?.also {
        data.add(TARGET_FIELD.with(it))
      }
      data
    }
  }
}