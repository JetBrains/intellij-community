// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.newui

import com.intellij.ide.plugins.PluginSource
import com.intellij.ide.plugins.PluginUiModel

/*
 * A service that executes operations on plugins, depending on the source of the plugin.
 * If it is local, the operation will be executed locally; otherwise, it will be executed on the server using an RPC call.
 */
class PluginModelFacade(private val pluginModel: MyPluginModel) {
  fun isPluginInstallingOrUpdating(model: PluginUiModel): Boolean {
    return getController(model).isPluginInstallingOrUpdating(model)
  }

  fun getController(model: PluginUiModel): PluginManagerController {
    return when (model.source) {
      PluginSource.LOCAL -> LocalPluginManagerController.getInstance()
      PluginSource.REMOTE -> TODO()
    }
  }

  //Temporary field that will allow as to change code in small parts
  fun getModel(): MyPluginModel = pluginModel
}

interface PluginManagerController {
  fun isPluginInstallingOrUpdating(model: PluginUiModel): Boolean
}