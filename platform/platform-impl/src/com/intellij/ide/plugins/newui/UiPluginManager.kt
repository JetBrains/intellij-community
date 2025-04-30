// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.newui

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import org.jetbrains.annotations.ApiStatus

/*
  Executes operations on plugins. Will have several implementations depending on registry option/rem dev mode.
  It serves the same purpose as PluginModelFacade but is stateless.
 */
@Service
@ApiStatus.Internal
class UiPluginManager {
  fun getPlugins(): List<PluginUiModel> {
    return getController().getPlugins()
  }


  fun getController(): UiPluginManagerController {
    return DefaultUiPluginManagerController
  }

  companion object {
    @JvmStatic
    fun getInstance(): UiPluginManager = service()
  }
}