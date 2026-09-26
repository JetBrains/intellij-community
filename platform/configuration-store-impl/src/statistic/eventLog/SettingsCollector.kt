// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.configurationStore.statistic.eventLog

import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector
import com.intellij.openapi.project.Project

internal class SettingsCollector : ApplicationUsagesCollector() {

  override fun getGroup(): EventLogGroup = GROUP
  override fun getMetrics(): Set<MetricEvent> = emptySet()

  companion object {
    private val GROUP = EventLogGroup("settings", 10)

    /**
      Event "option" is used for tests only
     */
    private val OPTION_EVENT = GROUP.registerVarargEvent("option",
                                                         SettingsFields.ID_FIELD,
                                                         SettingsFields.PLUGIN_INFO_FIELD,
                                                         SettingsFields.COMPONENT_FIELD,
                                                         SettingsFields.NAME_FIELD,
                                                         SettingsFields.DEFAULT_PROJECT_FIELD,
                                                         SettingsFields.DEFAULT_FIELD,
                                                         SettingsFields.TYPE_FIELD,
                                                         SettingsFields.VALUE_FIELD)

    private val NOT_DEFAULT_EVENT = GROUP.registerVarargEvent("not.default",
                                                              SettingsFields.ID_FIELD,
                                                              SettingsFields.PLUGIN_INFO_FIELD,
                                                              SettingsFields.COMPONENT_FIELD,
                                                              SettingsFields.NAME_FIELD,
                                                              SettingsFields.DEFAULT_PROJECT_FIELD,
                                                              SettingsFields.TYPE_FIELD,
                                                              SettingsFields.VALUE_FIELD)

    private val INVOKED_EVENT = GROUP.registerVarargEvent("invoked",
                                                          SettingsFields.ID_FIELD,
                                                          SettingsFields.PLUGIN_INFO_FIELD,
                                                          SettingsFields.COMPONENT_FIELD,
                                                          SettingsFields.DEFAULT_PROJECT_FIELD)

    @JvmStatic
    fun logOption(project: Project?, data: List<EventPair<*>>) = OPTION_EVENT.logState(project, data)

    @JvmStatic
    fun logNotDefault(project: Project?, data: List<EventPair<*>>) = NOT_DEFAULT_EVENT.logState(project, data)

    @JvmStatic
    fun logInvoked(project: Project?, data: List<EventPair<*>>) = INVOKED_EVENT.logState(project, data)
  }
}