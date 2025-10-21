// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.execution.dashboard

import com.intellij.execution.Executor
import com.intellij.execution.RunContentDescriptorIdImpl
import com.intellij.execution.dashboard.RunDashboardServiceId
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.ide.rpc.ComponentDirectTransferId
import com.intellij.ide.rpc.setupTransfer
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.platform.execution.dashboard.splitApi.RunDashboardLuxedContentEvent
import com.intellij.platform.ide.productMode.IdeProductMode.Companion.isBackend
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.ConcurrentHashMap
import javax.swing.JComponent

@ApiStatus.Internal
@Service(Service.Level.PROJECT)
class BackendLuxedRunDashboardContentManager(val project: Project, val scope: CoroutineScope) {
  companion object {
    @JvmStatic
    fun getInstance(project: Project): BackendLuxedRunDashboardContentManager {
      return project.getService(BackendLuxedRunDashboardContentManager::class.java)
    }
  }

  private val registeredComponents = ConcurrentHashMap<RunDashboardServiceId, RegisteredComponent>()
  private val luxedContents = MutableSharedFlow<RunDashboardLuxedContentEvent>(replay = 10, extraBufferCapacity = 10, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  fun registerToolWindowContentForLuxingIfNecessary(descriptor: RunContentDescriptor, executor: Executor) {
    if (isBackend && ToolWindowId.RUN == executor.getId()) {
      val descriptorId = descriptor.id as? RunContentDescriptorIdImpl ?: return
      val serviceId = guessServiceSuitableForDescriptor(descriptor) ?: return
      disposeLuxForComponent(serviceId)
      registeredComponents[serviceId] = RegisteredComponent(descriptor.component, serviceId, descriptorId, null)
      luxedContents.tryEmit(RunDashboardLuxedContentEvent(serviceId, descriptorId, ToolWindowId.RUN, true))
    }
  }

  fun unregisterLuxedToolWindowContent(descriptor: RunContentDescriptor, executor: Executor) {
    // might consider backend debugger support, but it is already sooo complicated - let's rather NOT
    if (isBackend && ToolWindowId.DEBUG == executor.getId()) {
      val serviceId = guessServiceSuitableForDescriptor(descriptor) ?: return
      val disposedComponentOrNull = disposeLuxForComponent(serviceId)
      val disposedDescriptorId = disposedComponentOrNull?.descriptorId ?: return
      luxedContents.tryEmit(RunDashboardLuxedContentEvent(serviceId, disposedDescriptorId, ToolWindowId.DEBUG, false))
    }
  }

  @RequiresEdt
  fun startLuxing(id: RunDashboardServiceId): ComponentDirectTransferId? {
    val registeredComponent = registeredComponents[id] ?: return null
    val luxDisposable = Disposer.newDisposable()
    registeredComponent.activeDisposable = luxDisposable
    val transfer = registeredComponent.component.setupTransfer(luxDisposable)

    thisLogger().warn("BACKEND DASHBOARD LUX: start for $id")

    return transfer
  }

  fun pauseLuxing(id: RunDashboardServiceId) {
    thisLogger().warn("BACKEND DASHBOARD LUX: pause for $id")
    pauseLuxForComponent(id)
  }

  fun getLuxedContents(): Flow<RunDashboardLuxedContentEvent> {
    return luxedContents.asSharedFlow()
  }

  private fun guessServiceSuitableForDescriptor(descriptor: RunContentDescriptor): RunDashboardServiceId? {
    val managerImpl = RunDashboardManagerImpl.getInstance(project)
    val descriptorId = descriptor.id ?: return null
    val settings = managerImpl.findSettings(descriptorId) ?: return null
    // lookup by settings, descriptor id is not assigned for run toolwindow since it is not split
    // get any, not sure how to distinguish between same run configs in the list :(
    return managerImpl.getServices(settings)?.firstOrNull()?.uuid
  }

  private fun pauseLuxForComponent(serviceId: RunDashboardServiceId) {
    val registeredComponent = registeredComponents[serviceId] ?: return

    val activeDisposable = registeredComponent.activeDisposable
    assert(activeDisposable != null) {
      "activeDisposable is null for $serviceId while LUXing component is still active - somebody likely changed the backend model incorrectly"
    }
    Disposer.dispose(activeDisposable!!)
    registeredComponent.activeDisposable = null
  }

  private fun disposeLuxForComponent(serviceId: RunDashboardServiceId): RegisteredComponent? {
    val registeredComponent = registeredComponents.remove(serviceId) ?: return null
    val existingServiceOrNull = RunDashboardManagerImpl.getInstance(project).findServiceById(serviceId)

    // detach descriptor only if nobody had changed it before - might happen if LUXed run is replaced by frontend debug and vice versa
    if (existingServiceOrNull != null && existingServiceOrNull.descriptorId == registeredComponent.descriptorId) {
      RunDashboardManagerImpl.getInstance(project).detachServiceRunContentDescriptor(registeredComponent.descriptorId)
    }

    val activeDisposable = registeredComponent.activeDisposable
    if (activeDisposable != null) {
      Disposer.dispose(activeDisposable)
    }
    else {
      thisLogger().debug("activeDisposable is already null for $serviceId. Service was not selected -> LUX was paused, nothing to dispose of")
    }
    return registeredComponent
  }

  private data class RegisteredComponent(
    val component: JComponent,
    val serviceId: RunDashboardServiceId,
    val descriptorId: RunContentDescriptorIdImpl,
    var activeDisposable: Disposable?,
  )
}
