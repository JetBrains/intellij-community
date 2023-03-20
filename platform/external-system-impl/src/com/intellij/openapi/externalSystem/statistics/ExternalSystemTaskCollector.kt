// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.statistics

import com.intellij.execution.impl.statistics.RunConfigurationUsageTriggerCollector.RunTargetValidator
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.externalSystem.statistics.ExternalSystemActionsCollector.Companion.EXTERNAL_SYSTEM_ID

class ExternalSystemTaskCollector : CounterUsagesCollector() {
  companion object {
    val GROUP = EventLogGroup("external.project.task", 3)

    @JvmField
    val TASK_ID_FIELD = EventFields.Enum<ExternalSystemUsagesCollector.ExternalSystemTaskId>("task_id")

    @JvmField
    val TARGET_FIELD = EventFields.StringValidatedByCustomRule("target", RunTargetValidator::class.java)


    @JvmField
    val EXTERNAL_TASK_ACTIVITY = GROUP.registerIdeActivity(null, startEventAdditionalFields = arrayOf(TASK_ID_FIELD,
                                                                                                      TARGET_FIELD,
                                                                                                      EXTERNAL_SYSTEM_ID))
  }

  override fun getGroup(): EventLogGroup {
    return GROUP
  }
}

