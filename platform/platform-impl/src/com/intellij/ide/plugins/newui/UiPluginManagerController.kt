// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.newui

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.PluginId
import org.jetbrains.annotations.ApiStatus

/*
 A controller that executes operations on plugins. There will be several implementations. It serves the same purpose as PluginModelFacade but is stateless.
 */
@ApiStatus.Internal
interface UiPluginManagerController {
  fun getPlugins(): List<PluginUiModel>
  fun getVisiblePlugins(showImplementationDetails: Boolean): List<PluginUiModel>
  fun getInstalledPlugins(): List<PluginUiModel>
  fun isPluginDisabled(pluginId: PluginId): Boolean

  companion object {
    val EP_NAME: ExtensionPointName<UiPluginManagerController> = ExtensionPointName<UiPluginManagerController>("com.intellij.uiPluginManagerController")
  }
}