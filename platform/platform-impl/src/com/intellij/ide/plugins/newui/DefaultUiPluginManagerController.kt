// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.newui

import com.intellij.ide.plugins.InstalledPluginsState
import com.intellij.ide.plugins.PluginManager
import com.intellij.ide.plugins.PluginManagerCore
import org.jetbrains.annotations.ApiStatus
import com.intellij.openapi.extensions.PluginId

@ApiStatus.Internal
object DefaultUiPluginManagerController : UiPluginManagerController {
  override fun getPlugins(): List<PluginUiModel> {
    return PluginManagerCore.plugins.map { PluginUiModelAdapter(it) }
  }

  override fun getVisiblePlugins(showImplementationDetails: Boolean): List<PluginUiModel> {
    return PluginManager.getVisiblePlugins(showImplementationDetails).map { PluginUiModelAdapter(it) }.toList()
  }

  override fun getInstalledPlugins(): List<PluginUiModel> {
    return InstalledPluginsState.getInstance().installedPlugins.map { PluginUiModelAdapter(it) }
  }

  override fun isPluginDisabled(pluginId: PluginId): Boolean {
    return PluginManagerCore.isDisabled(pluginId)
  }
}