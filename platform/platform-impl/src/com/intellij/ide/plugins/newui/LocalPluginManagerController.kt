// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.newui

import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.PluginEnableDisableAction
import com.intellij.ide.plugins.PluginEnabledState
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.getPluginDistDirByClass
import com.intellij.ide.plugins.pluginRequiresUltimatePluginButItsDisabled
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ex.ApplicationInfoEx
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent

@ApiStatus.Internal
class LocalPluginManagerController(private val localPluginModel: MyPluginModel) : PluginManagerController {
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
}