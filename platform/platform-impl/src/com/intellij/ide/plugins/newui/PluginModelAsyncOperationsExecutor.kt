// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.newui

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object PluginModelAsyncOperationsExecutor {
  fun performUninstall(
    cs: CoroutineScope,
    descriptor: PluginUiModel,
    sessionId: String,
    controller: UiPluginManagerController,
    callback: (Boolean) -> Unit,
  ) {
    cs.launch {
      val needRestart = controller.performUninstall(sessionId, descriptor.pluginId)
      withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
        callback(needRestart)
      }
    }
  }
}