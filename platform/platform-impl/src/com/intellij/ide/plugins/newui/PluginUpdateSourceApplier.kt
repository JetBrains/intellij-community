// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.newui

import com.intellij.ide.plugins.marketplace.InstallPluginResult
import com.intellij.openapi.updateSettings.impl.PluginUpdateSourceId
import com.intellij.openapi.updateSettings.impl.PluginUpdateSourceService
import com.intellij.openapi.updateSettings.impl.PluginUpdateSourceServiceImpl
import com.intellij.openapi.updateSettings.impl.createRepository
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

internal class PluginUpdateSourceApplier private constructor(
  private val pluginModel: PluginUiModel,
  private val modelFacade: PluginModelFacade,
  private val initialUpdateSource: PluginUpdateSourceId?,
  private val wasInitialUpdateSourceExplicit: Boolean,
) {

  companion object {
    suspend fun createApplier(pluginModel: PluginUiModel, modelFacade: PluginModelFacade): PluginUpdateSourceApplier {
      // Case when pluginUpdateSource was edited in settings, then plugin was updated but failed is ignored for now,
      // because in future pluginUpdateSource won't be updated on update. For uninstalled plugin one can't edit pluginUpdateSource
      val initialUpdateSource = modelFacade.getPluginUpdateSource(pluginModel.pluginId)
      val wasInitialUpdateSourceExplicit =
        (PluginUpdateSourceService.getInstance() as PluginUpdateSourceServiceImpl).hasExplicitlySetPluginUpdateSource(pluginModel.pluginId)
      return PluginUpdateSourceApplier(pluginModel, modelFacade, initialUpdateSource, wasInitialUpdateSourceExplicit)
    }
  }

  suspend fun runWithRevertOnException(block: suspend () -> Unit) {
    modelFacade.persistPluginUpdateSource(pluginModel.pluginId, createRepository(pluginModel))
    try {
      block.invoke()
    }
    catch (ex: Exception) {
      withContext(NonCancellable) {
        revertApplyingPluginUpdateSourceId()
      }
      throw ex
    }
  }

  private suspend fun revertApplyingPluginUpdateSourceId() {
    if (wasInitialUpdateSourceExplicit) {
      modelFacade.persistPluginUpdateSource(pluginModel.pluginId, initialUpdateSource)
    }
    else {
      modelFacade.persistPluginUpdateSource(pluginModel.pluginId, null)
    }
  }

  suspend fun applyPluginUpdateSourcesBasedOnResult(result: InstallPluginResult?) {
    if (result == null || !result.success) {
      revertApplyingPluginUpdateSourceId()
    }
    else {
      result.dependentPluginUpdateSourceIds.filter { it.key != pluginModel.pluginId }.forEach { (id, sourceId) ->
        modelFacade.persistPluginUpdateSource(id, sourceId)
      }
    }
  }
}