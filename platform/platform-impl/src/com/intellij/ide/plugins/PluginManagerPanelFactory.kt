// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.ide.plugins.marketplace.PluginSearchResult
import com.intellij.ide.plugins.newui.PluginUiModel
import com.intellij.ide.plugins.newui.UiPluginManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.registry.RegistryManager.Companion.getInstance
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object PluginManagerPanelFactory {
  private val LOG = Logger.getInstance(PluginManagerPanelFactory::class.java)

  fun createMarketplacePanel(cs: CoroutineScope, callback: (CreateMarketplacePanelModel) -> Unit) {
    cs.launch {
      val pluginManager = UiPluginManager.getInstance()
      val marketplaceData = mutableMapOf<String, PluginSearchResult>()

      val queries = listOf(
        "is_featured_search=true",
        "orderBy=update+date",
        "orderBy=downloads",
        "orderBy=rating"
      )

      try {
        for (query in queries) {
          val result = pluginManager.executeMarketplaceQuery(query, 18, false)
          if (result.error == null) {
            marketplaceData[query] = result
          }
        }
      } catch (e: Exception) {
        LOG.info("Main plugin repository is not available (${e.message}). Please check your network settings.")
      }

      withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
        callback(CreateMarketplacePanelModel(marketplaceData))
      }
    }
  }

  @ApiStatus.Internal
  fun createInstalledPanel(cs: CoroutineScope, callback: (CreateInstalledPanelModel) -> Unit) {
    cs.launch {
      val pluginManager = UiPluginManager.getInstance()
      val installedPlugins = pluginManager.getInstalledPlugins()
      val visiblePlugins = pluginManager.getVisiblePlugins(Registry.`is`("plugins.show.implementation.details"))
      withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
        callback(CreateInstalledPanelModel(installedPlugins, visiblePlugins))
      }
    }
  }
}

@Service
@ApiStatus.Internal
class PluginManagerCoroutineScopeHolder(val coroutineScope: CoroutineScope)

data class CreateInstalledPanelModel(
  val installedPlugins: List<PluginUiModel>,
  val visiblePlugins: List<PluginUiModel>,
)

data class CreateMarketplacePanelModel(
  val marketplaceData: Map<String, PluginSearchResult>,
)