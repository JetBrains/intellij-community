// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.execution.dashboard.splitApi.frontend

import com.intellij.execution.RunContentDescriptorIdImpl
import com.intellij.execution.dashboard.RunDashboardServiceId
import com.intellij.ide.rpc.getComponent
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.platform.execution.dashboard.splitApi.RunDashboardServiceRpc
import com.intellij.platform.execution.serviceView.FrontendServiceViewLuxComponent
import com.intellij.platform.project.projectId
import com.intellij.ui.components.panels.Wrapper
import fleet.rpc.client.RpcClientDisconnectedException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class FrontendDashboardLuxComponent(scope: CoroutineScope,
                                    val id: RunDashboardServiceId,
                                    val backendDescriptorId: RunContentDescriptorIdImpl,
                                    val executorId: String,
                                    private val project: Project) : Wrapper(), FrontendServiceViewLuxComponent {
  private val requestedBinding = MutableStateFlow(false)

  init {
    scope.launch(Dispatchers.EDT) {
      var bound = false
      try {
        requestedBinding.collect { requested ->
          if (requested != bound) {
            bound = requested
            updateBinding(requested)
          }
        }
      }
      finally {
        if (bound) {
          withContext(NonCancellable) {
            updateBinding(false)
          }
        }
      }
    }
  }

  fun bind() {
    requestedBinding.value = true
  }

  fun unbind() {
    requestedBinding.value = false
  }

  private suspend fun updateBinding(bind: Boolean) {
    try {
      if (bind) {
        val componentInfo = RunDashboardServiceRpc.getInstance().startLuxingContentForService(project.projectId(), id)
        val component = componentInfo?.getComponent()
        setContent(component)
      }
      else {
        setContent(null)
        RunDashboardServiceRpc.getInstance().pauseLuxingContentForService(project.projectId(), id)
      }
    }
    catch (_: RpcClientDisconnectedException) {
      // The backend side of the luxed content is already gone.
      setContent(null)
    }
  }
}