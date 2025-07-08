// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.newui

import com.intellij.ide.plugins.marketplace.ApplyPluginsStateResult
import com.intellij.ide.plugins.marketplace.CheckErrorsResult
import com.intellij.ide.plugins.marketplace.SetEnabledStateResult
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent

@ApiStatus.Internal
object PluginModelAsyncOperationsExecutor {
  fun performUninstall(
    cs: CoroutineScope,
    descriptor: PluginUiModel,
    sessionId: String,
    controller: UiPluginManagerController,
    callback: (Boolean, Map<PluginId, CheckErrorsResult>) -> Unit,
  ) {
    cs.launch {
      val needRestart = controller.performUninstall(sessionId, descriptor.pluginId)
      descriptor.isDeleted = true
      PluginManagerCustomizer.getInstance()?.onPluginDeleted(descriptor, controller.getTarget())
      val errors = UiPluginManager.getInstance().loadErrors(sessionId)
      withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
        callback(needRestart, errors)
      }
    }
  }

  fun enablePlugins(
    cs: CoroutineScope,
    sessionId: String,
    descriptorIds: List<PluginId>,
    enable: Boolean,
    project: Project?,
    callback: (SetEnabledStateResult) -> Unit,
  ) {
    cs.launch(Dispatchers.IO) {
      val result = UiPluginManager.getInstance().enablePlugins(sessionId, descriptorIds, enable, project)
      withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
        callback(result)
      }
    }
  }
}