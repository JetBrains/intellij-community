// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.task.impl

import com.intellij.internal.statistic.IdeActivityDefinition
import com.intellij.internal.statistic.collectors.fus.ClassNameRuleValidator
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.*
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector

object ProjectTaskManagerStatisticsCollector : CounterUsagesCollector() {
  val GROUP: EventLogGroup = EventLogGroup("build", 7)

  @JvmField
  val TASK_RUNNER: StringListEventField = EventFields.StringListValidatedByCustomRule("task_runner_class",
                                                                                      ClassNameRuleValidator::class.java)

  @JvmField
  val MODULES: IntEventField = EventFields.Int("modules")

  @JvmField
  val INCREMENTAL: BooleanEventField = EventFields.Boolean("incremental")

  @JvmField
  val BUILD_ORIGINATOR: StringEventField = EventFields.StringValidatedByCustomRule("build_originator", ClassNameRuleValidator::class.java)

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