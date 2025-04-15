// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.newui

import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.PluginEnabledState
import javax.swing.JComponent
import com.intellij.openapi.application.ModalityState

/*
 * A service that executes operations on plugins, depending on the source of the plugin.
 * If it is local, the operation will be executed locally; otherwise, it will be executed on the server using an RPC call.
 */
class PluginModelFacade(private val pluginModel: MyPluginModel) {
  private val localController = LocalPluginManagerController(pluginModel)

  fun isPluginInstallingOrUpdating(model: PluginUiModel): Boolean {
    return getController(model).isPluginInstallingOrUpdating(model)
  }

  fun getState(model: PluginUiModel): PluginEnabledState {
    return getController(model).getState(model)
  }

  fun enable(model: PluginUiModel) {
    getController(model).enable(model)
  }

  fun disable(model: PluginUiModel) {
    getController(model).disable(model)
  }

  fun installOrUpdatePlugin(component: JComponent, model: PluginUiModel, updateDescriptor: IdeaPluginDescriptor?, modalityState: ModalityState) {
    getController(model).installOrUpdatePlugin(component, model, updateDescriptor, modalityState)
  }

  fun addUninstalled(model: PluginUiModel) {
    getController(model).addUninstalled(model)
  }

  fun getIcon(model: PluginUiModel, big: Boolean, error: Boolean, disabled: Boolean): javax.swing.Icon {
    return getController(model).getIcon(model, big, error, disabled)
  }

  private fun getController(model: PluginUiModel): PluginManagerController {
    return when (model.source) {
      PluginSource.LOCAL -> localController
      PluginSource.REMOTE -> TODO()
    }
  }

  //Temporary field that will allow as to change code in small parts
  fun getModel(): MyPluginModel = pluginModel
}

interface PluginManagerController {
  fun isPluginInstallingOrUpdating(model: PluginUiModel): Boolean
  fun getState(model: PluginUiModel): PluginEnabledState
  fun disable(model: PluginUiModel)
  fun installOrUpdatePlugin(component: JComponent, model: PluginUiModel, updateDescriptor: IdeaPluginDescriptor?, modalityState: ModalityState)
  fun addUninstalled(model: PluginUiModel)
  fun enable(model: PluginUiModel)
  fun getIcon(model: PluginUiModel, big: Boolean, error: Boolean, disabled: Boolean): javax.swing.Icon
}