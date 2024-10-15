// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.diagnostic.plugin.freeze

import com.intellij.openapi.components.*
import com.intellij.openapi.extensions.PluginId
import org.jetbrains.annotations.ApiStatus
import java.time.Instant
import java.time.temporal.ChronoUnit

@Service(Service.Level.APP)
@State(
  name = "PluginFreezes",
  storages = [Storage(value = "pluginFreezes.xml")]
)
internal class PluginsFreezesService : PersistentStateComponent<PluginsFreezesServiceState> {
  private var state: PluginsFreezesServiceState = PluginsFreezesServiceState()

  companion object {
    private const val NOTIFICATION_COOLDOWN_DAYS = 1L

    @JvmStatic
    fun getInstance(): PluginsFreezesService = service()
  }

  fun mutePlugin(pluginId: PluginId) {
    state.mutedPlugins[pluginId.idString] = true
  }

  fun setLatestFreezeDate(pluginId: PluginId) {
    state.latestNotificationData[pluginId.idString] = Instant.now().toString()
  }

  fun shouldBeIgnored(pluginId: PluginId): Boolean {
    val pluginIdString = pluginId.idString
    if (state.mutedPlugins[pluginIdString] == true) return true

    val lastNotification = state.latestNotificationData[pluginIdString]?.let { Instant.parse(it) } ?: return false
    return Instant.now().isBefore(lastNotification.plus(NOTIFICATION_COOLDOWN_DAYS, ChronoUnit.DAYS))
  }

  override fun getState(): PluginsFreezesServiceState = state

  override fun loadState(state: PluginsFreezesServiceState) {
    this.state = state
  }
}

@ApiStatus.Internal
data class PluginsFreezesServiceState(
  var latestNotificationData: MutableMap<String, String> = mutableMapOf(),
  var mutedPlugins: MutableMap<String, Boolean> = mutableMapOf(),
)
