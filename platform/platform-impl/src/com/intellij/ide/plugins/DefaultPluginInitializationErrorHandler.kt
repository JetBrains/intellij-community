// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.util.ui.RawSwingDispatcher
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

@ApiStatus.Internal
class DefaultPluginInitializationErrorHandler : PluginInitializationErrorHandler {

  private val pluginLoadingErrors: List<@Nls String> by lazy {
    PluginManagerCore.getAndClearPluginLoadingErrors().map { it.message }
  }

  private val pluginsToEnableDisable: Pair<List<PluginStateChangeData>, List<PluginStateChangeData>> by lazy {
    PluginManagerCore.getStartupActionsPluginsToEnableDisable()
  }

  private val pluginEnabler: PluginEnabler by lazy { PluginEnabler.getInstance() }

  override suspend fun getPluginInitializationErrors(): PluginInitializationErrors {
    return PluginInitializationErrors(
      pluginErrors = pluginLoadingErrors,
      pluginNamesToEnable = pluginsToEnableDisable.first.map { it.pluginName },
      pluginNamesToDisable = pluginsToEnableDisable.second.map { it.pluginName }
    )
  }

  private fun findDescriptors(data: List<PluginStateChangeData>, markedForLoading: Boolean): List<IdeaPluginDescriptorImpl> {
    val ids = data.mapTo(HashSet()) { it.pluginId }
    return PluginManagerCore.getPluginSet().allPlugins.filter { it.getPluginId() in ids }
      .onEach { it.isMarkedForLoading = markedForLoading }
  }

  override suspend fun enableDeferredPlugins() {
    withContext(RawSwingDispatcher) {
      if (pluginEnabler.enable(findDescriptors(pluginsToEnableDisable.first, markedForLoading = true))) {
        PluginManagerMain.notifyPluginsUpdated(null)
      }
    }
  }

  override suspend fun disableDeferredPlugins() {
    withContext(RawSwingDispatcher) {
      pluginEnabler.disable(findDescriptors(pluginsToEnableDisable.second, markedForLoading = false))
    }
  }
}
