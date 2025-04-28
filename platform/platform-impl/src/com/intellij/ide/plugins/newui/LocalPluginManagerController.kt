// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.newui

import com.intellij.ide.plugins.IdeaPluginDependency
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.PageContainer
import com.intellij.ide.plugins.PluginEnableDisableAction
import com.intellij.ide.plugins.PluginEnabledState
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.PluginNode
import com.intellij.ide.plugins.marketplace.IntellijPluginMetadata
import com.intellij.ide.plugins.marketplace.MarketplaceRequests
import com.intellij.ide.plugins.marketplace.MarketplaceSearchPluginData
import com.intellij.ide.plugins.marketplace.PluginReviewComment
import com.intellij.ide.plugins.marketplace.utils.MarketplaceUrls
import com.intellij.ide.plugins.pluginRequiresUltimatePluginButItsDisabled
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ex.ApplicationInfoEx
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent

@ApiStatus.Internal
class LocalPluginManagerController(private val localPluginModel: MyPluginModel) : PluginManagerController {
  private val marketplaceRequests = MarketplaceRequests.getInstance()

  override fun isPluginInstallingOrUpdating(model: PluginUiModel): Boolean {
    return MyPluginModel.isInstallingOrUpdate(model.pluginId)
  }

  override fun getState(model: PluginUiModel): PluginEnabledState {
    return localPluginModel.getState(model.pluginId)
  }

  override fun enable(model: PluginUiModel) {
    localPluginModel.enable(listOf(model.getDescriptor()))
  }

  override fun disable(model: PluginUiModel) {
    localPluginModel.disable(listOf(model.getDescriptor()))
  }

  override fun installOrUpdatePlugin(component: JComponent, model: PluginUiModel, updateDescriptor: IdeaPluginDescriptor?, modalityState: ModalityState) {
    localPluginModel.installOrUpdatePlugin(component, model.getDescriptor(), updateDescriptor, modalityState)
  }

  override fun addUninstalled(model: PluginUiModel) {
    localPluginModel.addUninstalled(model.getDescriptor())
  }

  override fun getIcon(model: PluginUiModel, big: Boolean, error: Boolean, disabled: Boolean): javax.swing.Icon {
    return localPluginModel.getIcon(model.getDescriptor(), big, error, disabled)
  }

  override fun enableRequiredPlugins(model: PluginUiModel) {
    localPluginModel.enableRequiredPlugins(model.getDescriptor())
  }

  override fun isEnabled(model: PluginUiModel): Boolean {
    return localPluginModel.isEnabled(model.getDescriptor())
  }

  override fun isPluginRequiredForProject(model: PluginUiModel): Boolean {
    return localPluginModel.isRequiredPluginForProject(model.pluginId)
  }

  override fun hasPluginRequiresUltimateButItsDisabled(models: List<PluginUiModel>): Boolean {
    val idMap = PluginManagerCore.buildPluginIdMap()
    return models.any { pluginRequiresUltimatePluginButItsDisabled(it.pluginId, idMap) }
  }

  override fun setEnabledState(models: List<PluginUiModel>, action: PluginEnableDisableAction) {
    localPluginModel.setEnabledState(models.map { it.getDescriptor() }, action)
  }

  override fun getDependents(models: List<PluginUiModel>): Map<PluginUiModel, List<PluginUiModel>> {
    val applicationInfo = ApplicationInfoEx.getInstanceEx()
    val idMap = PluginManagerCore.buildPluginIdMap()
    return models.associateWith { MyPluginModel.getDependents(it.getDescriptor(), applicationInfo, idMap).map(::PluginUiModelAdapter) }
  }

  override fun isBundledUpdate(model: PluginUiModel): Boolean {
    return MyPluginModel.isBundledUpdate(model.getDescriptor())
  }

  override fun uninstallAndUpdateUi(model: PluginUiModel) {
    return localPluginModel.uninstallAndUpdateUi(model.getDescriptor())
  }

  override fun findInstalledPlugin(model: PluginUiModel): PluginUiModel? {
    return PluginManagerCore.getPlugin(model.pluginId)?.let { PluginUiModelAdapter(it) }
  }

  override fun findPlugin(model: PluginUiModel): PluginUiModel? {
    return PluginManagerCore.buildPluginIdMap()[model.pluginId]?.let { PluginUiModelAdapter(it) }
  }

  override fun getPluginManagerUrl(model: PluginUiModel): String {
    return MarketplaceUrls.getPluginManagerUrl()
  }

  override fun isDisabledInDiff(model: PluginUiModel): Boolean {
    return localPluginModel.isDisabledInDiff(model.pluginId)
  }

  override fun loadPluginDetails(model: PluginUiModel): PluginUiModel? {
    return marketplaceRequests.loadPluginDetails(model)
  }

  override fun loadAllPluginDetails(existingModel: PluginUiModel, targetModel: PluginUiModel): PluginUiModel? {
    val existingNode = existingModel.getPluginDescriptor() as? PluginNode ?: return null
    val marketPlaceNode = targetModel.getPluginDescriptor() as? PluginNode ?: return null
    if (!existingNode.suggestedFeatures.isEmpty()) {
      marketPlaceNode.suggestedFeatures = existingNode.suggestedFeatures
    }

    val metadata = marketplaceRequests.loadPluginMetadata(existingNode)
    if (metadata != null) {
      if (metadata.screenshots != null) {
        marketPlaceNode.setScreenShots(metadata.screenshots)
        marketPlaceNode.externalPluginIdForScreenShots = existingNode.externalPluginId
      }
      metadata.toPluginNode(marketPlaceNode)
    }
    fetchReviews(targetModel)
    fetchDependecyNames(targetModel)
    return targetModel
  }

  override fun fetchReviews(targetModel: PluginUiModel): PluginUiModel? {
    val reviewComments = PageContainer<PluginReviewComment>(20, 0)
    reviewComments.addItems(loadPluginReviews(targetModel, reviewComments.nextPage))
    (targetModel.getDescriptor() as PluginNode).setReviewComments(reviewComments)
    return targetModel
  }

  override fun loadPluginReviews(targetModel: PluginUiModel, page: Int): List<PluginReviewComment> {
    return marketplaceRequests.loadPluginReviews(targetModel.getPluginDescriptor() as PluginNode, page) ?: emptyList()
  }

  override fun isLoaded(pluginUiModel: PluginUiModel): Boolean {
    return localPluginModel.isLoaded(pluginUiModel.pluginId)
  }

  override fun fetchDependecyNames(targetModel: PluginUiModel): PluginUiModel? {
    val resultNode = targetModel.getPluginDescriptor() as? PluginNode ?: return null
    resultNode.dependencyNames = resultNode.dependencies.asSequence()
      .filter { !it.isOptional }
      .map(IdeaPluginDependency::pluginId)
      .filter { isNotPlatformAlias(it) }
      .map { pluginId ->
        PluginManagerCore.findPlugin(pluginId)?.let {
          return@map it.name
        }
        marketplaceRequests.getLastCompatiblePluginUpdate(pluginId)?.name ?: pluginId.idString
      }
      .toList()

    return targetModel
  }

  override fun getLastCompatiblePluginUpdate(model: PluginUiModel): PluginUiModel? {
    return marketplaceRequests.getLastCompatiblePluginUpdateModel(model.pluginId)
  }

  override fun loadPluginMetadata(pluginId: String): IntellijPluginMetadata? {
    return marketplaceRequests.loadPluginMetadata(pluginId)
  }
}
