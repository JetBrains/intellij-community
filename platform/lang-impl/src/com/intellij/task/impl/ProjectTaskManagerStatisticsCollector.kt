// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.task.impl

import com.intellij.internal.statistic.IdeActivityDefinition
import com.intellij.internal.statistic.collectors.fus.ClassNameRuleValidator
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.BooleanEventField
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.IntEventField
import com.intellij.internal.statistic.eventLog.events.StringListEventField
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector

class ProjectTaskManagerStatisticsCollector : CounterUsagesCollector() {
  companion object {
    val GROUP: EventLogGroup = EventLogGroup("build", 6)

    @JvmField
    val TASK_RUNNER: StringListEventField = EventFields.StringListValidatedByCustomRule("task_runner_class", ClassNameRuleValidator::class.java)

    @JvmField
    val MODULES: IntEventField = EventFields.Int("modules")

    @JvmField
    val INCREMENTAL: BooleanEventField = EventFields.Boolean("incremental")

    @JvmField
    val HAS_ERRORS: BooleanEventField = EventFields.Boolean("has_errors")

    @JvmField
    val BUILD_ACTIVITY: IdeActivityDefinition = GROUP.registerIdeActivity(null,
                                                                          startEventAdditionalFields = arrayOf(TASK_RUNNER, EventFields.PluginInfo, MODULES, INCREMENTAL),
                                                                          finishEventAdditionalFields = arrayOf(HAS_ERRORS))
  }

  override fun getGroup(): EventLogGroup {
    return GROUP
  }
}