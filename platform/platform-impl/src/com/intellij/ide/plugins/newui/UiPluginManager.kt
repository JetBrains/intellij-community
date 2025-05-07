// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.newui

import com.intellij.ide.plugins.marketplace.IdeCompatibleUpdate
import com.intellij.ide.plugins.marketplace.IntellijUpdateMetadata
import com.intellij.ide.plugins.marketplace.MarketplaceSearchPluginData
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.registry.Registry
import org.jetbrains.annotations.ApiStatus

/*
  Executes operations on plugins. Will have several implementations depending on registry option/rem dev mode.
  It serves the same purpose as PluginModelFacade but is stateless.
 */
@Service
@ApiStatus.Internal
class UiPluginManager {
  fun getPlugins(): List<PluginUiModel> {
    return getController().getPlugins()
  }

  fun executeMarketplaceQuery(query: String, count: Int, includeUpgradeToCommercialIde: Boolean): List<MarketplaceSearchPluginData> {
    return getController().executeMarketplaceQuery(query, count, includeUpgradeToCommercialIde)
  }

  fun getVisiblePlugins(showImplementationDetails: Boolean): List<PluginUiModel> {
    return getController().getVisiblePlugins(showImplementationDetails)
  }

  fun getInstalledPlugins(): List<PluginUiModel> {
    return getController().getInstalledPlugins()
  }

  fun isPluginDisabled(pluginId: PluginId): Boolean {
    return getController().isPluginDisabled(pluginId)
  }

  fun loadUpdateMetadata(xmlId: String, ideCompatibleUpdate: IdeCompatibleUpdate, indicator: ProgressIndicator? = null): IntellijUpdateMetadata {
    return getController().loadUpdateMetadata(xmlId, ideCompatibleUpdate, indicator)
  }

  fun getController(): UiPluginManagerController {
    if (Registry.`is`("reworked.plugin.manager.enabled")) {
      return UiPluginManagerController.EP_NAME.extensionList.firstOrNull() ?: DefaultUiPluginManagerController
    }
    return DefaultUiPluginManagerController
  }

  companion object {
    @JvmStatic
    fun getInstance(): UiPluginManager = service()
  }
}