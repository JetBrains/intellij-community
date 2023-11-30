// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.statistics

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventFields.BoundedInt
import com.intellij.internal.statistic.eventLog.events.EventFields.Float
import com.intellij.internal.statistic.eventLog.events.EventFields.Int
import com.intellij.internal.statistic.eventLog.events.EventFields.RoundedInt
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.externalSystem.statistics.ExternalSystemActionsCollector.EXTERNAL_SYSTEM_ID

object ProjectImportCollector : CounterUsagesCollector() {
  val GROUP = EventLogGroup("project.import", 10)

  @JvmField
  val TASK_CLASS = EventFields.Class("task_class")

  @JvmField
  val SUBMODULES_COUNT = RoundedInt("submodules_count")


  @JvmField
  val LINKED_PROJECTS = Int("linked_projects")

  @JvmField
  val ROOT_PROJECTS = Int("root_projects")

  @JvmField
  val RESOLVED_DEPENDENCIES = BoundedInt("resolved_dependencies", intArrayOf(0, 5, 10, 50, 100, 500, 1000, 5000))

  @JvmField
  val ADDED_MODULES = RoundedInt("added_modules")

  @JvmField
  val RESOLVED_DEPS_PERCENT = Float("resolved_dependencies_percent")

  @JvmField
  val IMPORT_ACTIVITY = GROUP.registerIdeActivity("import_project", startEventAdditionalFields = arrayOf(EXTERNAL_SYSTEM_ID, TASK_CLASS,
                                                                                                         EventFields.PluginInfo),
                                                  finishEventAdditionalFields = arrayOf(SUBMODULES_COUNT, LINKED_PROJECTS))

  @JvmField
  val PREIMPORT_ACTIVITY = GROUP.registerIdeActivity("fast_model_read", parentActivity = IMPORT_ACTIVITY,
                                                     finishEventAdditionalFields = arrayOf(SUBMODULES_COUNT,
                                                                                           ROOT_PROJECTS,
                                                                                           LINKED_PROJECTS,
                                                                                           RESOLVED_DEPENDENCIES,
                                                                                           RESOLVED_DEPS_PERCENT,
                                                                                           ADDED_MODULES))

  @JvmField
  val REAPPLY_MODEL_ACTIVITY = GROUP.registerIdeActivity("reapply_model_import_project",
                                                         startEventAdditionalFields = arrayOf(EXTERNAL_SYSTEM_ID, TASK_CLASS,
                                                                                              EventFields.PluginInfo),
                                                         finishEventAdditionalFields = arrayOf(SUBMODULES_COUNT, LINKED_PROJECTS))

  @JvmField
  val IMPORT_STAGE = GROUP.registerIdeActivity("stage", startEventAdditionalFields = arrayOf(TASK_CLASS),
                                               parentActivity = IMPORT_ACTIVITY)


  @JvmField
  val READ_STAGE = GROUP.registerIdeActivity("read",
                                             parentActivity = IMPORT_ACTIVITY)

  @JvmField
  val RESOLVE_STAGE = GROUP.registerIdeActivity("resolve",
                                                parentActivity = IMPORT_ACTIVITY)

  @JvmField
  val WORKSPACE_APPLY_STAGE = GROUP.registerIdeActivity("workspace_import",
                                                        parentActivity = IMPORT_ACTIVITY)

  @JvmField
  val PROJECT_CONFIGURATION_STAGE = GROUP.registerIdeActivity("configure",
                                                              parentActivity = IMPORT_ACTIVITY)

  @JvmField
  val PLUGIN_RESOLVE_PROCESS = GROUP.registerIdeActivity("resolve_plugins",
                                                         parentActivity = null)

  override fun getGroup(): EventLogGroup = GROUP
}