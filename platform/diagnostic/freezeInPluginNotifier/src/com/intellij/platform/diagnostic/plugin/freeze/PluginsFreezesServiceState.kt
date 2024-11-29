// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.diagnostic.plugin.freeze

import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.components.*
import com.intellij.openapi.extensions.PluginId
import java.time.Instant
import java.time.temporal.ChronoUnit

private const val NOTIFICATION_COOLDOWN_DAYS: Long = 1L

@Service(Service.Level.APP)
@State(
  name = "PluginFreezes",
  storages = [Storage(value = "pluginFreezes.xml", roamingType = RoamingType.DISABLED)]
)
internal class PluginsFreezesService : PersistentStateComponent<PluginsFreezesServiceState> {
  private var state: PluginsFreezesServiceState = PluginsFreezesServiceState()

  companion object {
    @JvmStatic
    fun getInstance(): PluginsFreezesService = service()
  }

  fun mutePlugin(pluginId: PluginId) {
    val ideVersion = mutedProblemVersion()

    state.mutedPlugins += MutedPlugin().apply {
      this.pluginId = pluginId.idString
      this.ideVersion = ideVersion
    }

    state.mutedPlugins.removeIf { it.ideVersion != ideVersion }
  }

  private fun mutedProblemVersion(): String {
    val app = ApplicationInfo.getInstance()
    return app.majorVersion + "." + app.minorVersion
  }

  fun setLatestFreezeDate(pluginId: PluginId) {
    state.latestNotificationTime[pluginId.idString] = Instant.now().toString()
  }

  fun reset() {
    state.latestNotificationTime.clear()
    state.mutedPlugins.clear()
  }

  fun shouldBeIgnored(pluginId: PluginId): Boolean {
    val pluginIdString = pluginId.idString

    val ideVersion = mutedProblemVersion()
    if (state.mutedPlugins.any { p -> p.pluginId == pluginIdString && p.ideVersion == ideVersion }) {
      return true
    }

    val lastNotification = state.latestNotificationTime[pluginIdString]?.let { Instant.parse(it) } ?: return false
    return Instant.now().isBefore(lastNotification.plus(NOTIFICATION_COOLDOWN_DAYS, ChronoUnit.DAYS))
  }

  override fun getState(): PluginsFreezesServiceState = state

  override fun loadState(state: PluginsFreezesServiceState) {
    this.state = state
  }
}

internal data class PluginsFreezesServiceState(
  var latestNotificationTime: MutableMap<String, String> = mutableMapOf(),
  var mutedPlugins: MutableList<MutedPlugin> = mutableListOf(),
)

internal class MutedPlugin: BaseState() {
  var pluginId by string()
  var ideVersion by string()
}