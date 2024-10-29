// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.task.impl

import com.intellij.internal.statistic.IdeActivityDefinition
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.*
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector

internal object ProjectTaskManagerStatisticsCollector : CounterUsagesCollector() {
  val GROUP: EventLogGroup = EventLogGroup("build", 8)

  @JvmField
  val TASK_RUNNER: ClassListEventField = EventFields.ClassList("task_runner_class")

  @JvmField
  val MODULES: IntEventField = EventFields.Int("modules")

  @JvmField
  val INCREMENTAL: BooleanEventField = EventFields.Boolean("incremental")

  @JvmField
  val BUILD_ORIGINATOR: ClassEventField = EventFields.Class("build_originator")

  @JvmField
  val HAS_ERRORS: BooleanEventField = EventFields.Boolean("has_errors")

  @JvmField
  val BUILD_ACTIVITY: IdeActivityDefinition = GROUP.registerIdeActivity(null,
                                                                        startEventAdditionalFields = arrayOf(TASK_RUNNER,
                                                                                                             EventFields.PluginInfo,
                                                                                                             MODULES, INCREMENTAL,
                                                                                                             BUILD_ORIGINATOR),
                                                                        finishEventAdditionalFields = arrayOf(TASK_RUNNER, MODULES,
                                                                                                              INCREMENTAL, BUILD_ORIGINATOR,
                                                                                                              HAS_ERRORS))

  override fun getGroup(): EventLogGroup = GROUP
}