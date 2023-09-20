// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.statistics

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventFields.Int
import com.intellij.internal.statistic.eventLog.events.EventFields.RoundedInt
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.externalSystem.statistics.ExternalSystemActionsCollector.EXTERNAL_SYSTEM_ID

object ProjectImportCollector : CounterUsagesCollector() {
  val GROUP = EventLogGroup("project.import", 8)

  @JvmField
  val TASK_CLASS = EventFields.Class("task_class")

  @JvmField
  val SUBMODULES_COUNT = RoundedInt("submodules_count")


  @JvmField
  val LINKED_PROJECTS = Int("linked_projects")

  @JvmField
  val IMPORT_ACTIVITY = GROUP.registerIdeActivity("import_project", startEventAdditionalFields = arrayOf(EXTERNAL_SYSTEM_ID, TASK_CLASS,
                                                                                                         EventFields.PluginInfo),
                                                  finishEventAdditionalFields = arrayOf(SUBMODULES_COUNT, LINKED_PROJECTS))

  @JvmField
  val IMPORT_STAGE = GROUP.registerIdeActivity("stage", startEventAdditionalFields = arrayOf(TASK_CLASS),
                                               parentActivity = IMPORT_ACTIVITY)


  override fun getGroup(): EventLogGroup = GROUP
}