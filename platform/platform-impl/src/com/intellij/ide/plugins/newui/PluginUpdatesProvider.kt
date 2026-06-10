// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.newui

import com.intellij.ide.plugins.api.PluginDto
import com.intellij.openapi.extensions.ExtensionPointName
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

/**
 * Interface responsible for providing plugin update events and managing the update process.
 *
 * This interface is intended for internal use and allows clients to subscribe to a flow of plugin update events
 * or trigger the update mechanism for plugins.
 *
 * Currently, there are two implementations of this interface: {@link DefaultPluginUpdatesProvider} in monolith and frontend
 * and {@link BackendPluginUpdatesProvider} in frontend-split mode.
 */
@ApiStatus.Internal
interface PluginUpdatesProvider {
  suspend fun pluginUpdateEvents(): Flow<PluginUpdatesEvent?>
  suspend fun update()

  companion object {
    val EP_NAME: ExtensionPointName<PluginUpdatesProvider> = ExtensionPointName.create("com.intellij.pluginUpdatesProvider")

    @JvmStatic
    fun getInstances(): Collection<PluginUpdatesProvider> = EP_NAME.extensionList
  }
}

/**
 * Represents an event triggered when plugin updates occur, is a mirror of {@link InternalPluginResults}
 */
@ApiStatus.Internal
@Serializable
data class PluginUpdatesEvent(val enabledUpdates: List<PluginDto>, val disabledUpdates: List<PluginDto>, val pluginNods: List<PluginDto>) {
  val all: Collection<PluginDto> by lazy { enabledUpdates + disabledUpdates }
}
