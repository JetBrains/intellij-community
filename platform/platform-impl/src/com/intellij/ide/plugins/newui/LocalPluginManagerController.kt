// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.newui

import com.intellij.ide.plugins.PluginUiModel
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service

@Service
class LocalPluginManagerController : PluginManagerController {
  override fun isPluginInstallingOrUpdating(model: PluginUiModel): Boolean {
    return MyPluginModel.isInstallingOrUpdate(model.pluginId)
  }

  companion object {
    fun getInstance(): LocalPluginManagerController = service()
  }
}