// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.ide.plugins.marketplace.PluginSearchResult
import com.intellij.ide.plugins.newui.MyPluginModel
import com.intellij.ide.plugins.newui.PluginLogo
import com.intellij.ide.plugins.newui.PluginUiModel
import com.intellij.ide.plugins.newui.UiPluginManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.findSuggestedPlugins
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.HtmlChunk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object PluginManagerPanelFactory {
  private val LOG = Logger.getInstance(PluginManagerPanelFactory::class.java)

  fun createMarketplacePanel(cs: CoroutineScope, myPluginModel: MyPluginModel, project: Project?, callback: (CreateMarketplacePanelModel) -> Unit) {
    cs.launch(Dispatchers.IO) {
      myPluginModel.waitForSessionInitialization()
      val customRepositoriesMap = UiPluginManager.getInstance().getCustomRepositoryPluginMap()
      val suggestedPlugins = if (project != null) findSuggestedPlugins(project, customRepositoriesMap) else emptyList()
      val pluginManager = UiPluginManager.getInstance()
      val marketplaceData = mutableMapOf<String, PluginSearchResult>()

      val queries = listOf(
        "is_featured_search=true",
        "orderBy=update+date",
        "orderBy=downloads",
        "orderBy=rating"
      )

      val errorCheckResults = pluginManager.loadErrors(myPluginModel.mySessionId.toString())
      val errors = MyPluginModel.getErrors(errorCheckResults)
      try {
        for (query in queries) {
          val result = pluginManager.executeMarketplaceQuery(query, 18, false)
          if (result.error == null) {
            marketplaceData[query] = result
          }
        }
      }
      catch (e: Exception) {
        LOG.info("Main plugin repository is not available (${e.message}). Please check your network settings.")
      }
      val installedPlugins = pluginManager.findInstalledPlugins(marketplaceData.flatMap { it.value.getPlugins().map { plugin -> plugin.pluginId } }.toSet())
      withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
        callback(CreateMarketplacePanelModel(marketplaceData, errors, suggestedPlugins, customRepositoriesMap, installedPlugins))
      }
    }
  }

  @ApiStatus.Internal
  fun createInstalledPanel(cs: CoroutineScope, myPluginModel: MyPluginModel, callback: (CreateInstalledPanelModel) -> Unit) {
    cs.launch(Dispatchers.IO) {
      myPluginModel.waitForSessionInitialization()
      val pluginManager = UiPluginManager.getInstance()
      val installedPlugins = pluginManager.getInstalledPlugins()
      val visiblePlugins = pluginManager.getVisiblePlugins(Registry.`is`("plugins.show.implementation.details"))
      val errorCheckResults = pluginManager.loadErrors(myPluginModel.mySessionId.toString())
      val errors = MyPluginModel.getErrors(errorCheckResults)
      withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
        try {
          PluginLogo.startBatchMode()
          callback(CreateInstalledPanelModel(installedPlugins, visiblePlugins, errors))
        }
        finally {
          PluginLogo.endBatchMode()
        }
      }
    }
  }
}

@Service
@ApiStatus.Internal
class PluginManagerCoroutineScopeHolder(val coroutineScope: CoroutineScope)

@ApiStatus.Internal
data class CreateInstalledPanelModel(
  val installedPlugins: List<PluginUiModel>,
  val visiblePlugins: List<PluginUiModel>,
  val errors: Map<PluginId, List<HtmlChunk>>,
)

@ApiStatus.Internal
data class CreateMarketplacePanelModel(
  val marketplaceData: Map<String, PluginSearchResult>,
  val errors: Map<PluginId, List<HtmlChunk>>,
  val suggestedPlugins: List<PluginUiModel>,
  val customRepositories: Map<String, List<PluginUiModel>>,
  val installedPlugins: Map<PluginId, PluginUiModel>,
)