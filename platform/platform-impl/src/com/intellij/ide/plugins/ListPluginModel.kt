// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.ide.plugins.newui.PluginInstallationState
import com.intellij.ide.plugins.newui.PluginUiModel
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.text.HtmlChunk
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
data class ListPluginModel(
  val installedModels: MutableMap<PluginId, PluginUiModel> = mutableMapOf(),
  val errors: MutableMap<PluginId, List<HtmlChunk>> = mutableMapOf(),
  val pluginInstallationStates: MutableMap<PluginId, PluginInstallationState> = mutableMapOf(),
) {

  fun setInstalledPlugins(models: Map<PluginId, PluginUiModel>) {
    installedModels.clear()
    installedModels.putAll(models)
  }

  fun setErrors(models: Map<PluginId, List<HtmlChunk>>) {
    errors.clear()
    errors.putAll(models)
  }

  fun setErrors(pluginId: PluginId, errors: List<HtmlChunk>) {
    this.errors[pluginId] = errors
  }

  fun setPluginInstallationStates(models: Map<PluginId, PluginInstallationState>) {
    pluginInstallationStates.clear()
    pluginInstallationStates.putAll(models)
  }

  fun setPluginInstallationState(pluginId: PluginId, state: PluginInstallationState) {
    pluginInstallationStates[pluginId] = state
  }
}