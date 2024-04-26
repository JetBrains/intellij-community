// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.configurationStore.statistic.eventLog

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.project.Project

internal object SettingsChangesCollector : CounterUsagesCollector() {

  override fun getGroup(): EventLogGroup = GROUP

  private val GROUP = EventLogGroup("settings.changes", 58)

  private val COMPONENT_CHANGED_OPTION_EVENT = GROUP.registerVarargEvent("component_changed_option",
                                                                         SettingsFields.ID_FIELD,
                                                                         SettingsFields.PLUGIN_INFO_FIELD,
                                                                         SettingsFields.COMPONENT_FIELD,
                                                                         SettingsFields.NAME_FIELD,
                                                                         SettingsFields.DEFAULT_PROJECT_FIELD,
                                                                         SettingsFields.TYPE_FIELD,
                                                                         SettingsFields.VALUE_FIELD)

  private val COMPONENT_CHANGED_EVENT = GROUP.registerVarargEvent("component_changed",
                                                                  SettingsFields.ID_FIELD,
                                                                  SettingsFields.PLUGIN_INFO_FIELD,
                                                                  SettingsFields.COMPONENT_FIELD,
                                                                  SettingsFields.DEFAULT_PROJECT_FIELD)

  @JvmStatic
  fun logComponentChangedOption(project: Project?, data: List<EventPair<*>>) = COMPONENT_CHANGED_OPTION_EVENT.log(project, data)

  @JvmStatic
  fun logComponentChanged(project: Project?, data: List<EventPair<*>>) = COMPONENT_CHANGED_EVENT.log(project, data)
}