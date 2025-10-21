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
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
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
            if (requested) {
              doBind()
            }
            else {
              doUnbind()
            }
          }
        }
      }
      finally {
        if (bound) {
          withContext(NonCancellable) {
            doUnbind()
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

  private suspend fun doBind() {
    val componentInfo = RunDashboardServiceRpc.getInstance().startLuxingContentForService(project.projectId(), id)
    val component = componentInfo?.getComponent()
    setContent(component)
  }

  private suspend fun doUnbind() {
    setContent(null)
    RunDashboardServiceRpc.getInstance().pauseLuxingContentForService(project.projectId(), id)
  }
}