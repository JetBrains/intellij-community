// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl

import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector
import com.intellij.openapi.application.PluginAutoUpdater

private class PluginAutoUpdateFUSCollector : ApplicationUsagesCollector() {
  private val GROUP = EventLogGroup("ide.plugins.autoupdate", 1)

  override fun getGroup(): EventLogGroup = GROUP

  private val SUCCESS = EventFields.Boolean("success")
  private val UPDATES_PREPARED = EventFields.Int("updates_prepared")
  private val PLUGINS_UPDATED = EventFields.Int("plugins_updated")

  private val AUTOUPDATE_SUCCESS = GROUP.registerEvent("autoupdate.success", SUCCESS)
  private val AUTOUPDATE_RESULT = GROUP.registerEvent("autoupdate.result", UPDATES_PREPARED, PLUGINS_UPDATED)

  override fun getMetrics(): Set<MetricEvent> {
    val autoupdateResult = PluginAutoUpdater.getPluginAutoUpdateResult()
    return when {
      autoupdateResult == null -> emptySet()
      autoupdateResult.isFailure -> setOf(AUTOUPDATE_SUCCESS.metric(false))
      else -> {
        val autoupdateStats = autoupdateResult.getOrNull()!!
        if (autoupdateStats.updatesPrepared == 0 && autoupdateStats.pluginsUpdated == 0) {
          emptySet()
        } else {
          setOf(
            AUTOUPDATE_SUCCESS.metric(true),
            AUTOUPDATE_RESULT.metric(autoupdateStats.updatesPrepared, autoupdateStats.pluginsUpdated)
          )
        }
      }
    }
  }
}