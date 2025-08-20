// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.collectors.fus.plugins

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.IntEventField
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.util.IntellijInternalApi
import org.jetbrains.annotations.ApiStatus

/**
 * Should be in RD-only module, but FUS automation does not work there
 */
@ApiStatus.Internal
@IntellijInternalApi
object PluginSyncUsageCollector : CounterUsagesCollector() {
  private val GROUP = EventLogGroup("ide.plugins.rd.plugin.sync", version = 1)

  private val CLIENT_PLUGINS_TO_SYNC = IntEventField("client_plugins_to_sync")
  private val HOST_PLUGINS_TO_SYNC = IntEventField("host_plugins_to_sync")
  private val CLIENT_SELECTED_PLUGINS_TO_SYNC = IntEventField("client_selected_plugins_to_sync")
  private val HOST_SELECTED_PLUGINS_TO_SYNC = IntEventField("host_selected_plugins_to_sync")

  private val PLUGIN_SYNC_AVAILABLE_NOTIFICATION_SHOWN = GROUP.registerEvent("plugin.sync.available.notification.shown")
  private val PLUGIN_SYNC_ADVICE_SHOWN = GROUP.registerEvent(
    "plugin.sync.advice.shown",
    CLIENT_PLUGINS_TO_SYNC,
    HOST_PLUGINS_TO_SYNC)
  private val PLUGIN_SYNC_STARTED = GROUP.registerEvent(
    "plugin.sync.started",
    CLIENT_SELECTED_PLUGINS_TO_SYNC,
    HOST_SELECTED_PLUGINS_TO_SYNC)
  private val PLUGIN_SYNC_COMPLETED = GROUP.registerEvent("plugin.sync.completed")

  override fun getGroup(): EventLogGroup = GROUP

  fun logPluginSyncAvailableNotificationShown() {
    PLUGIN_SYNC_AVAILABLE_NOTIFICATION_SHOWN.log()
  }

  fun logPluginSyncAdviceShown(clientPluginsToSync: Int, hostPluginsToSync: Int) {
    PLUGIN_SYNC_ADVICE_SHOWN.log(clientPluginsToSync, hostPluginsToSync)
  }

  fun logPluginSyncStarted(clientSelectedPluginsToSync: Int, hostSelectedPluginsToSync: Int) {
    PLUGIN_SYNC_STARTED.log(clientSelectedPluginsToSync, hostSelectedPluginsToSync)
  }

  fun logPluginSyncCompleted() {
    PLUGIN_SYNC_COMPLETED.log()
  }
}