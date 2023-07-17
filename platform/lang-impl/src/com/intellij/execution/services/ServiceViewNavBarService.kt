// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.services

import com.intellij.execution.services.ServiceModel.ServiceViewItem
import com.intellij.execution.services.ServiceViewNavBarExtension.ServiceViewNavBarItem
import com.intellij.ide.navbar.impl.DefaultNavBarItem
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.util.childScope
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow

@Service(Service.Level.PROJECT)
internal class ServiceViewNavBarService(val project: Project, val cs: CoroutineScope) {
  companion object {
    @JvmStatic
    fun getInstance(project: Project): ServiceViewNavBarService = project.service()
  }

  fun createNavBarPanel(serviceView: ServiceView,
                        selector: ServiceViewNavBarSelector): ServiceViewNavBarPanel {
    @OptIn(ExperimentalCoroutinesApi::class)
    val childScope = cs.childScope(Dispatchers.Default.limitedParallelism(1))
    Disposer.register(serviceView) {
      childScope.cancel()
    }

    val updateRequests: MutableSharedFlow<Unit> = MutableSharedFlow(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    return ServiceViewNavBarPanel(project, childScope, updateRequests, serviceView.model, selector) {
      childScope.launch {
        val item = ((it.pointer.dereference() as? DefaultNavBarItem<*>)?.data as? ServiceViewNavBarItem)?.item ?: return@launch
        withContext(Dispatchers.EDT) {
          selector.select(item)
        }
      }
    }
  }

  internal interface ServiceViewNavBarSelector {
    fun select(item: ServiceViewItem)

    fun getSelectedItem(): ServiceViewItem?
  }
}
