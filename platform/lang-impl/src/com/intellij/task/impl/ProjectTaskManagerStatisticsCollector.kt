// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.task.impl

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector

class ProjectTaskManagerStatisticsCollector : CounterUsagesCollector() {
  companion object {
    val GROUP = EventLogGroup("build", 4)

    @JvmField
    val TASK_RUNNER = EventFields.StringListValidatedByCustomRule("task_runner_class", "class_name")

    @JvmField
    val BUILD_ACTIVITY = GROUP.registerIdeActivity(null, startEventAdditionalFields = arrayOf(TASK_RUNNER, EventFields.PluginInfo))
  }

  override fun getGroup(): EventLogGroup {
    return GROUP
  }
}