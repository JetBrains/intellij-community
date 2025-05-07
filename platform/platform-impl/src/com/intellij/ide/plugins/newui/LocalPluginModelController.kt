// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.newui

import com.intellij.ide.plugins.IdeaPluginDependency
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.IdeaPluginDescriptorImpl
import com.intellij.ide.plugins.PageContainer
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
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ex.ApplicationInfoEx
import com.intellij.openapi.util.text.HtmlChunk
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent

@ApiStatus.Internal
class LocalPluginModelController(private val localPluginModel: MyPluginModel) : PluginModelController {
  private val marketplaceRequests = MarketplaceRequests.getInstance()

  override fun isPluginInstallingOrUpdating(model: PluginUiModel): Boolean {
    return MyPluginModel.isInstallingOrUpdate(model.pluginId)
  }

  override fun getState(model: PluginUiModel): PluginEnabledState {
    return localPluginModel.getState(model.pluginId)
  }

  override fun enable(models: List<PluginUiModel>) {
    localPluginModel.enable(models.map { it.getDescriptor() })
  }

  override fun disable(models: List<PluginUiModel>) {
    localPluginModel.disable(models.map { it.getDescriptor() })
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

  override fun hasPluginForEnableDisable(models: List<PluginUiModel>): Boolean {
    val idMap = PluginManagerCore.buildPluginIdMap()
    return models.any { !pluginRequiresUltimatePluginButItsDisabled(it.pluginId, idMap) }
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
    fetchReviews(targetModel)
    fetchDependecyNames(targetModel)
    return targetModel
  }

  override fun fetchReviews(targetModel: PluginUiModel): PluginUiModel? {
    val reviewComments = ReviewsPageContainer(20, 0)
    reviewComments.addItems(loadPluginReviews(targetModel, reviewComments.getNextPage()))
    targetModel.reviewComments = reviewComments
    return targetModel
  }

  override fun loadPluginReviews(targetModel: PluginUiModel, page: Int): List<PluginReviewComment> {
    return marketplaceRequests.loadPluginReviews(targetModel, page) ?: emptyList()
  }

  override fun isLoaded(pluginUiModel: PluginUiModel): Boolean {
    return localPluginModel.isLoaded(pluginUiModel.pluginId)
  }

  override fun finishInstall(model: PluginUiModel, installedModel: PluginUiModel?, finishedSuccessfully: Boolean, showErrors: Boolean, restartRequired: Boolean) {
    return localPluginModel.finishInstall(model.getDescriptor(), installedModel?.getDescriptor() as? IdeaPluginDescriptorImpl, finishedSuccessfully, showErrors, restartRequired)
  }

  override fun getErrors(model: PluginUiModel): List<HtmlChunk> {
    return localPluginModel.getErrors(model.getDescriptor())
  }

  override fun isUninstalled(model: PluginUiModel): Boolean {
    return localPluginModel.isUninstalled(model.getDescriptor())
  }

  override fun fetchDependecyNames(targetModel: PluginUiModel): PluginUiModel? {
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

  override fun getLastCompatiblePluginUpdate(model: PluginUiModel): PluginUiModel? {
    return marketplaceRequests.getLastCompatiblePluginUpdateModel(model.pluginId)
  }

  override fun loadPluginMetadata(pluginId: String): IntellijPluginMetadata? {
    return marketplaceRequests.loadPluginMetadata(pluginId)
  }
}
