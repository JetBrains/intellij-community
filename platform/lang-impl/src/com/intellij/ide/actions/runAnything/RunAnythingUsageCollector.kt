// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.runAnything

import com.intellij.ide.actions.runAnything.groups.RunAnythingCompletionGroup
import com.intellij.ide.actions.runAnything.groups.RunAnythingGroup
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.project.Project

internal object RunAnythingUsageCollector : CounterUsagesCollector() {
  override fun getGroup(): EventLogGroup = GROUP

  private val GROUP = EventLogGroup("actions.runAnything", 3)

  private val WITH_SHIFT_FIELD = EventFields.Boolean("with_shift")
  private val WITH_ALT_FIELD = EventFields.Boolean("with_alt")
  private val LIST_FIELD = EventFields.Class("list")
  private val GROUP_FIELD = EventFields.Class("group")
  private val EXECUTE = GROUP.registerVarargEvent("execute", WITH_SHIFT_FIELD, WITH_ALT_FIELD, LIST_FIELD, GROUP_FIELD,
                                                  EventFields.PluginInfo)
  private val CLICK_MORE = GROUP.registerVarargEvent("click.more", LIST_FIELD, GROUP_FIELD, EventFields.PluginInfo)

  @JvmStatic
  fun triggerExecCategoryStatistics(project: Project,
                                    groups: MutableCollection<out RunAnythingGroup>,
                                    clazz: Class<out RunAnythingSearchListModel>,
                                    index: Int,
                                    shiftPressed: Boolean,
                                    altPressed: Boolean) {
    for (i in index downTo 0) {
      val group = RunAnythingGroup.findGroup(groups, i)
      if (group != null) {
        EXECUTE.log(project,
                    LIST_FIELD.with(clazz),
                    GROUP_FIELD.with(getGroupClass(group)),
                    WITH_SHIFT_FIELD.with(shiftPressed),
                    WITH_ALT_FIELD.with(altPressed))
        break
      }
    }
  }

  @JvmStatic
  fun triggerMoreStatistics(project: Project,
                            group: RunAnythingGroup,
                            clazz: Class<out RunAnythingSearchListModel>) {
    CLICK_MORE.log(project, LIST_FIELD.with(clazz), GROUP_FIELD.with(getGroupClass(group)))
  }

  private fun getGroupClass(group: RunAnythingGroup): Class<*> {
    return if (group is RunAnythingCompletionGroup<*, *>) group.provider.javaClass
    else group.javaClass
  }
}