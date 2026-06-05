// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.newui

import com.intellij.ide.plugins.marketplace.InstallPluginResult
import com.intellij.openapi.updateSettings.impl.PluginUpdateSourceId
import com.intellij.openapi.updateSettings.impl.PluginUpdateSourceService

internal class PluginUpdateSourceApplier(private val pluginModel: PluginUiModel) {
  private val initialUpdateSource: PluginUpdateSourceId? =
    PluginUpdateSourceService.getInstance().getPluginUpdateSourceId(pluginModel.pluginId)

  fun applyPluginUpdateSourceId() {
    PluginUpdateSourceService.getInstance().setPluginUpdateSourceId(pluginModel)
  }

  private fun revertApplyingPluginUpdateSourceId() {
    if (initialUpdateSource == null) {
      PluginUpdateSourceService.getInstance().erasePluginUpdateSourceId(pluginModel.pluginId)
    }
    else {
      PluginUpdateSourceService.getInstance().setPluginUpdateSourceId(pluginModel.pluginId, initialUpdateSource)
    }
  }

  fun revertIfNeeded(result: InstallPluginResult?) {
    if (result == null || !result.success) {
      revertApplyingPluginUpdateSourceId()
    }
  }

  fun revertIfNeeded(cause: Throwable?) {
    if (cause != null) {
      revertApplyingPluginUpdateSourceId()
    }
  }
}