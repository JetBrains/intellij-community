// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.services

import com.intellij.execution.services.ServiceModel.ServiceViewItem
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel

@Service(Service.Level.PROJECT)
internal class ServiceViewNavBarService(val project: Project, val cs: CoroutineScope) {
  @OptIn(ExperimentalCoroutinesApi::class)
  private val dispatcher = Dispatchers.Default.limitedParallelism(1)

  companion object {
    @JvmStatic
    fun getInstance(project: Project): ServiceViewNavBarService = project.service()
  }

  fun createNavBarPanel(serviceView: ServiceView,
                        selector: ServiceViewNavBarSelector): ServiceViewNavBarPanel {
    val childScope = cs.childScope(dispatcher)
    Disposer.register(serviceView) {
      childScope.cancel()
    }

    return ServiceViewNavBarPanel(project, childScope, serviceView.model, selector)
  }

  internal interface ServiceViewNavBarSelector {
    fun select(item: ServiceViewItem)

    fun getSelectedItem(): ServiceViewItem?
  }
}
