// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.execution.dashboard.splitApi.frontend

import com.intellij.execution.dashboard.RunDashboardServiceId
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.platform.execution.dashboard.RunDashboardCoroutineScopeProvider
import com.intellij.platform.execution.dashboard.splitApi.RunDashboardLuxedContentEvent
import com.intellij.platform.execution.dashboard.splitApi.RunDashboardServiceRpc
import com.intellij.platform.project.projectId
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
      withContext(Dispatchers.EDT) {
        if (luxedContent.isAdded) {
          registerLuxContent(luxedContent)
        }
        else {
          unregisterLuxContent(luxedContent)
        }
      }
    }
  }

  private fun unregisterLuxContent(luxedContent: RunDashboardLuxedContentEvent) {
    val existingLux = luxedContentsMap[luxedContent.serviceId]

    val sameDescriptorIds = existingLux?.backendDescriptorId == luxedContent.contentDescriptorId
    // Only the descriptor identifies our lux unambiguously. An unregister event whose descriptor differs
    // from what we have stored is about another content (e.g. an old descriptor after a relaunch or
    // a service-id rekey) and must not evict the current entry.
    val shouldRemoveLux = existingLux != null && sameDescriptorIds

    if (shouldRemoveLux) {
      val removedLux = luxedContentsMap.remove(luxedContent.serviceId)
      removedLux?.unbind()
    }
  }

  private fun registerLuxContent(event: RunDashboardLuxedContentEvent) {
    val bindingScope = RunDashboardCoroutineScopeProvider.getInstance(project).cs.childScope("FrontendDashboardLuxComponent for service ${event.serviceId}")
    val wrappedComponent = FrontendDashboardLuxComponent(bindingScope, event.serviceId, event.contentDescriptorId, event.executorId, project)
    // Unbind any previous registration so its child scope / coroutines aren't leaked when the entry is overwritten
    // (the new register event isn't always preceded by an explicit unregister for the previous descriptor).
    val previous = luxedContentsMap.put(event.serviceId, wrappedComponent)
    previous?.unbind()
  }
}