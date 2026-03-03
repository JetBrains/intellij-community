// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.ide.plugins.newui.PluginUiModel
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.updateSettings.impl.PluginUpdateHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.util.function.Consumer

@ApiStatus.Internal
object PluginUpdateListener {
  private val LOG = logger<PluginUpdateListener>()

  @JvmStatic
  fun calculateUpdates(
    coroutineScope: CoroutineScope,
    filter: (PluginUiModel) -> Boolean,
    callback: (List<PluginUiModel>) -> Unit,
  ) {
    coroutineScope.launch(Dispatchers.IO) {
      val updates = try {
        val updatesModel = PluginUpdateHandler.getInstance().loadAndStorePluginUpdates(buildNumber = null)
        (updatesModel.pluginUpdates + updatesModel.disabledPluginUpdates).map { it as PluginUiModel }.filter { filter(it) }
      }
      catch (e: Throwable) {
        LOG.warn("Failed to load plugin updates from PluginUpdateHandler", e)
        emptyList()
      }
      withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
        callback(updates)
      }
    }
  }
}
