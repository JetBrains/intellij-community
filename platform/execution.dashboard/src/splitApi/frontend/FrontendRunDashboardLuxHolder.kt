// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.execution.dashboard.splitApi.frontend

import com.intellij.execution.dashboard.RunDashboardServiceId
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.platform.execution.dashboard.RunDashboardCoroutineScopeProvider
import com.intellij.platform.execution.dashboard.splitApi.RunDashboardLuxedContentEvent
import com.intellij.platform.execution.dashboard.splitApi.RunDashboardServiceRpc
import com.intellij.platform.project.projectId
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.CoroutineScope
import java.util.concurrent.ConcurrentHashMap

@Service(Service.Level.PROJECT)
internal class FrontendRunDashboardLuxHolder(val project: Project, val coroutineScope: CoroutineScope) {
  companion object {
    @JvmStatic
    fun getInstance(project: Project): FrontendRunDashboardLuxHolder {
      return project.getService(FrontendRunDashboardLuxHolder::class.java)
    }
  }

  private val luxedContentsMap = ConcurrentHashMap<RunDashboardServiceId, FrontendDashboardLuxComponent>()

  fun getComponentOrNull(id: RunDashboardServiceId): FrontendDashboardLuxComponent? {
    return luxedContentsMap[id]
  }

  internal suspend fun subscribeToRunToolwindowUpdates() {
    RunDashboardServiceRpc.getInstance().getLuxedContentEvents(project.projectId()).collect { luxedContent ->
      if (luxedContent.isAdded) {
        registerLuxContent(luxedContent)
      }
      else {
        unregisterLuxContent(luxedContent)
      }
    }
  }

  private fun unregisterLuxContent(luxedContent: RunDashboardLuxedContentEvent) {
    val existingLux = luxedContentsMap[luxedContent.serviceId]

    val luxExists = existingLux != null
    val sameExecutors = existingLux?.executorId == luxedContent.executorId
    val sameDescriptorIds = existingLux?.backendDescriptorId == luxedContent.contentDescriptorId
    val shouldRemoveLux = when {
      !luxExists -> false
      sameExecutors && !sameDescriptorIds -> true
      !sameExecutors && sameDescriptorIds -> true
      else -> false
    }

    if (shouldRemoveLux) {
      val removedLux = luxedContentsMap.remove(luxedContent.serviceId)
      removedLux?.unbind()
    }
  }

  private fun registerLuxContent(event: RunDashboardLuxedContentEvent) {
    val bindingScope = RunDashboardCoroutineScopeProvider.getInstance(project).cs.childScope("FrontendDashboardLuxComponent for service ${event.serviceId}")
    val wrappedComponent = FrontendDashboardLuxComponent(bindingScope, event.serviceId, event.contentDescriptorId, event.executorId, project)
    luxedContentsMap[event.serviceId] = wrappedComponent
  }
}