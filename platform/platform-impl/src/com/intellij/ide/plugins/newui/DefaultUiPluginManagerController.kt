// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.newui

import com.intellij.ide.plugins.PluginManagerCore
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object DefaultUiPluginManagerController : UiPluginManagerController {
  override fun getPlugins(): List<PluginUiModel> {
    return PluginManagerCore.plugins.map { PluginUiModelAdapter(it) }
  }

}