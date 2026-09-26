// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.PluginId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class DefaultPluginInitializationErrorHandler : PluginInitializationErrorHandler {
  private val pluginManagerState: PluginManagerStateSnapshot by lazy {
    checkNotNull(service<PluginManagerStateService>().getCurrentState()) { "Plugins are not initialized" }
  }

  private val pluginEnabler: PluginEnabler by lazy { PluginEnabler.getInstance() }

  override suspend fun getPluginInitializationErrors(): PluginInitializationErrors {
    return PluginInitializationErrors(
      pluginErrors = pluginManagerState.loadingErrors,
      pluginNamesToEnable = pluginManagerState.pluginsToEnable.values.toList(),
      pluginNamesToDisable = pluginManagerState.pluginsToDisable.values.toList()
    )
  }

  private fun findDescriptors(ids: Set<PluginId>): List<IdeaPluginDescriptorImpl> {
    return PluginManagerCore.getPluginSet().allPlugins.filter { it.getPluginId() in ids }
  }

  override suspend fun enableDeferredPlugins() {
    withContext(Dispatchers.EDT) {
      if (pluginEnabler.enable(findDescriptors(pluginManagerState.pluginsToEnable.keys))) {
        PluginManagerMain.notifyPluginsUpdated(null)
      }
    }
  }

  override suspend fun disableDeferredPlugins() {
    withContext(Dispatchers.EDT) {
      pluginEnabler.disable(findDescriptors(pluginManagerState.pluginsToDisable.keys))
    }
  }
}
