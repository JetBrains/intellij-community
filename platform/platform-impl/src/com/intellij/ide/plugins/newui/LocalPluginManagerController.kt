// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.newui

import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.PluginEnabledState
import com.intellij.ide.plugins.PluginUiModel
import com.intellij.openapi.application.ModalityState
import javax.swing.JComponent

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
}