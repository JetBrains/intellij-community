// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.newui

import com.intellij.ide.plugins.IdeaPluginDependency
import com.intellij.ide.plugins.PluginEnableDisableAction
import com.intellij.ide.plugins.PluginEnabledState
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.PluginNode
import com.intellij.ide.plugins.api.ReviewsPageContainer
import com.intellij.ide.plugins.marketplace.IntellijPluginMetadata
import com.intellij.ide.plugins.marketplace.MarketplaceRequests
import com.intellij.ide.plugins.marketplace.PluginReviewComment
import com.intellij.ide.plugins.marketplace.utils.MarketplaceUrls
import com.intellij.ide.plugins.pluginRequiresUltimatePluginButItsDisabled
import javax.swing.JComponent
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.openapi.wm.ex.ProgressIndicatorEx
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class PluginModelFacade(private val pluginModel: MyPluginModel) {
  private val marketplaceRequests = MarketplaceRequests.getInstance()

  fun isPluginInstallingOrUpdating(model: PluginUiModel): Boolean {
    return MyPluginModel.isInstallingOrUpdate(model.pluginId)
  }

  fun getState(model: PluginUiModel): PluginEnabledState {
    return pluginModel.getState(model.pluginId)
  }

  fun enable(model: PluginUiModel) {
    pluginModel.enable(listOf(model.getDescriptor()))
  }

  fun enable(models: Collection<PluginUiModel>) {
    pluginModel.enable(models.map { it.getDescriptor() })
  }

  fun disable(model: PluginUiModel) {
    pluginModel.disable(listOf(model.getDescriptor()))
  }

  fun disable(models: Collection<PluginUiModel>) {
    pluginModel.disable(models.map { it.getDescriptor() })
  }

  fun installOrUpdatePlugin(component: JComponent, model: PluginUiModel, updateDescriptor: PluginUiModel?, modalityState: ModalityState) {
    pluginModel.installOrUpdatePlugin(component, model, updateDescriptor, modalityState)
  }

  fun addUninstalled(model: PluginUiModel) {
    pluginModel.addUninstalled(model.getDescriptor())
  }

  fun getIcon(model: PluginUiModel, big: Boolean, error: Boolean, disabled: Boolean): javax.swing.Icon {
    return pluginModel.getIcon(model.getDescriptor(), big, error, disabled)
  }

  fun getErrors(model: PluginUiModel): List<HtmlChunk> {
    return pluginModel.getErrors(model.getDescriptor())
  }

  fun enableRequiredPlugins(model: PluginUiModel) {
    pluginModel.enableRequiredPlugins(model.getDescriptor())
  }

  fun isUninstalled(model: PluginUiModel): Boolean {
    return pluginModel.isUninstalled(model.getDescriptor())
  }

  fun isEnabled(model: PluginUiModel): Boolean {
    return pluginModel.isEnabled(model.getDescriptor())
  }

  fun finishInstall(model: PluginUiModel, installedModel: PluginUiModel?, success: Boolean, showErrors: Boolean, restartRequired: Boolean) {
    pluginModel.finishInstall(model, installedModel, success, showErrors, restartRequired)
  }

  fun isPluginRequiredForProject(model: PluginUiModel): Boolean {
    return pluginModel.isRequiredPluginForProject(model.pluginId)
  }

  fun addComponent(component: ListPluginComponent) {
    pluginModel.addComponent(component)
  }

  fun removeComponent(component: ListPluginComponent) {
    pluginModel.removeComponent(component)
  }

  fun hasPluginForEnableDisable(models: Collection<PluginUiModel>): Boolean {
    val idMap = PluginManagerCore.buildPluginIdMap()
    return models.any { !pluginRequiresUltimatePluginButItsDisabled(it.pluginId, idMap) }
  }

  fun setEnabledState(models: Collection<PluginUiModel>, action: PluginEnableDisableAction) {
    pluginModel.setEnabledState(models.map { it.getDescriptor() }, action)
  }

  fun uninstallAndUpdateUi(descriptor: PluginUiModel) {
    pluginModel.uninstallAndUpdateUi(descriptor)
  }

  fun findInstalledPlugin(model: PluginUiModel): PluginUiModel? {
    return PluginManagerCore.getPlugin(model.pluginId)?.let { PluginUiModelAdapter(it) }
  }

  fun findPlugin(model: PluginUiModel): PluginUiModel? {
    return PluginManagerCore.buildPluginIdMap()[model.pluginId]?.let { PluginUiModelAdapter(it) }
  }

  fun getPluginManagerUrl(model: PluginUiModel): String {
    return MarketplaceUrls.getPluginManagerUrl()
  }

  fun isDisabledInDiff(model: PluginUiModel): Boolean {
    return pluginModel.isDisabledInDiff(model.pluginId)
  }

  fun loadPluginDetails(model: PluginUiModel): PluginUiModel? {
    return marketplaceRequests.loadPluginDetails(model)
  }

  fun loadAllPluginDetails(existingModel: PluginUiModel, targetModel: PluginUiModel): PluginUiModel? {
    if (!existingModel.suggestedFeatures.isEmpty()) {
      targetModel.suggestedFeatures = existingModel.suggestedFeatures
    }

    val externalPluginId = existingModel.externalPluginId ?: return null
    val metadata = marketplaceRequests.loadPluginMetadata(externalPluginId)
    if (metadata != null) {
      if (metadata.screenshots != null) {
        targetModel.screenShots = metadata.screenshots
        targetModel.externalPluginIdForScreenShots = externalPluginId
      }
      metadata.toPluginUiModel(targetModel)
    }
    loadReviews(targetModel)
    loadDependencyNames(targetModel)
    return targetModel
  }

  fun getLastCompatiblePluginUpdate(model: PluginUiModel): PluginUiModel? {
    return marketplaceRequests.getLastCompatiblePluginUpdateModel(model.pluginId)
  }

  fun loadPluginMetadata(source: PluginSource, pluginId: String): IntellijPluginMetadata? {
    return marketplaceRequests.loadPluginMetadata(pluginId)
  }

  fun loadReviews(existingModel: PluginUiModel): PluginUiModel? {
    val reviewComments = ReviewsPageContainer(20, 0)
    reviewComments.addItems(loadPluginReviews(existingModel, reviewComments.getNextPage()))
    existingModel.reviewComments = reviewComments
    return existingModel
  }

  fun loadDependencyNames(targetModel: PluginUiModel): PluginUiModel? {
    val resultNode = targetModel.getDescriptor() as? PluginNode ?: return null
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

  fun loadPluginReviews(targetModel: PluginUiModel, page: Int): List<PluginReviewComment> {
    return marketplaceRequests.loadPluginReviews(targetModel, page) ?: emptyList()
  }

  fun isLoaded(model: PluginUiModel): Boolean {
    return pluginModel.isLoaded(model.pluginId)
  }

  fun getModel(): MyPluginModel = pluginModel

  companion object {
    @JvmStatic
    fun addProgress(model: PluginUiModel, indicator: ProgressIndicatorEx) {
      MyPluginModel.addProgress(model.getDescriptor(), indicator)
    }
    
    @JvmStatic
    fun removeProgress(model: PluginUiModel, indicator: ProgressIndicatorEx) {
      MyPluginModel.removeProgress(model.getDescriptor(), indicator)
    }
  }
}