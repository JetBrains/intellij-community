// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.execution.dashboard

import com.intellij.execution.Executor
import com.intellij.execution.RunContentDescriptorIdImpl
import com.intellij.execution.dashboard.RunDashboardService
import com.intellij.execution.dashboard.RunDashboardServiceId
import com.intellij.execution.impl.ExecutionManagerImpl
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.ide.rpc.ComponentDirectTransferId
import com.intellij.ide.rpc.setupTransfer
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.platform.execution.dashboard.splitApi.RunDashboardLuxedContentEvent
import com.intellij.platform.execution.dashboard.splitApi.RunDashboardServiceDto
import com.intellij.platform.ide.productMode.IdeProductMode.Companion.isBackend
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

  init {
    if (isBackend) {
      scope.launch {
        // Keep registeredComponents in sync with the live service list:
        //  - if a descriptor we hold a lux for is now owned by a different RunDashboardServiceId
        //    (e.g. after an additional service is promoted into the main slot in doDetachServiceRunContentDescriptor),
        //    follow the descriptor to the new id;
        //  - if a descriptor is no longer owned by any service, drop the registration entirely.
        // Update tears down the active lux disposable, which was set up on EDT — keep teardown on EDT too.
        RunDashboardManagerImpl.getInstance(project).servicesDto.collect { services ->
          withContext(Dispatchers.EDT) {
            updateRegistrations(services)
          }
        }
      }
    }
  }

  private fun updateRegistrations(services: List<RunDashboardServiceDto>) {
    if (registeredComponents.isEmpty()) return

    val ownerByDescriptor = HashMap<RunContentDescriptorIdImpl, RunDashboardServiceId>(services.size)
    for (dto in services) {
      val contentId = dto.contentId ?: continue
      ownerByDescriptor[contentId] = dto.uuid
    }
    for ((registeredKey, entry) in registeredComponents.toMap()) {
      val expectedKey = ownerByDescriptor[entry.descriptorId]
      if (expectedKey == null) {
        // The descriptor we hold a lux for is no longer owned by any service (e.g. its content was disposed
        // or the service was detached). Drop the registration, tear down any active lux, and notify the frontend.
        val removed = registeredComponents.remove(registeredKey) ?: continue
        removed.activeDisposable?.let { Disposer.dispose(it) }
        luxedContents.tryEmit(RunDashboardLuxedContentEvent(registeredKey, removed.descriptorId, ToolWindowId.RUN, false))
        continue
      }
      if (expectedKey != registeredKey) {
        rekeyLuxRegistration(registeredKey, expectedKey)
      }
    }
  }

  private fun rekeyLuxRegistration(oldServiceId: RunDashboardServiceId, newServiceId: RunDashboardServiceId) {
    if (oldServiceId == newServiceId) return
    val existing = registeredComponents.remove(oldServiceId) ?: return
    // tear down any active luxing — the frontend will rebind under the new service id when the new node is selected
    existing.activeDisposable?.let { Disposer.dispose(it) }
    val rekeyed = RegisteredComponent(existing.component, newServiceId, existing.descriptorId, null)
    registeredComponents[newServiceId] = rekeyed
    luxedContents.tryEmit(RunDashboardLuxedContentEvent(oldServiceId, existing.descriptorId, ToolWindowId.RUN, false))
    luxedContents.tryEmit(RunDashboardLuxedContentEvent(newServiceId, existing.descriptorId, ToolWindowId.RUN, true))
  }

  fun registerToolWindowContentForLuxingIfNecessary(descriptor: RunContentDescriptor, executor: Executor) {
    if (isBackend && ToolWindowId.DEBUG != executor.getId()) {
      val descriptorId = descriptor.id as? RunContentDescriptorIdImpl ?: return
      val service = guessServiceSuitableForDescriptor(descriptor) ?: return
      val serviceId = service.uuid
      disposeLuxForComponent(service, descriptor)
      registeredComponents[serviceId] = RegisteredComponent(descriptor.component, serviceId, descriptorId, null)
      luxedContents.tryEmit(RunDashboardLuxedContentEvent(serviceId, descriptorId, ToolWindowId.RUN, true))
    }
  }

  fun unregisterLuxedToolWindowContent(descriptor: RunContentDescriptor, executor: Executor) {
    if (isBackend && ToolWindowId.DEBUG == executor.getId()) {
      val service = findServiceReusedByDescriptor(descriptor)
                    ?: guessServiceSuitableForDescriptor(descriptor)
                    ?: return
      val disposedComponentOrNull = disposeLuxForComponent(service, descriptor)
      val disposedDescriptorId = disposedComponentOrNull?.descriptorId ?: return
      luxedContents.tryEmit(RunDashboardLuxedContentEvent(service.uuid, disposedDescriptorId, ToolWindowId.DEBUG, false))
    }
  }

  fun exchangeStaleDebugDescriptorIfNeeded(descriptor: RunContentDescriptor, executor: Executor) {
    if (!isBackend || ToolWindowId.DEBUG == executor.getId()) return
    val newDescriptorId = descriptor.id ?: return
    val runDashboardManager = RunDashboardManagerImpl.getInstance(project)
    val settings = runDashboardManager.findSettings(newDescriptorId) ?: return
    val services = runDashboardManager.getServices(settings) ?: return
    if (services.any { it.descriptorId == newDescriptorId }) return

    // Only services holding terminated hidden descriptors are exchanged: live debug sessions must keep their own
    // node, and non-hidden contents are exchanged by the content manager's own reuse machinery.
    val staleDebugService = services.find { service ->
      val serviceDescriptor = service.descriptor
      serviceDescriptor != null && serviceDescriptor.isHiddenContent &&
      (serviceDescriptor.processHandler?.isProcessTerminated ?: true)
    } ?: return
    val staleDescriptorId = staleDebugService.descriptorId ?: return
    runDashboardManager.updateServiceRunContentDescriptor(staleDescriptorId, newDescriptorId)
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

  private fun findServiceReusedByDescriptor(descriptor: RunContentDescriptor): RunDashboardService? {
    val newDescriptorId = descriptor.id ?: return null
    val environment = ExecutionManagerImpl.getInstance(project).getExecutionEnvironments(descriptor).firstOrNull() ?: return null
    val reusedDescriptorId = environment.contentToReuse?.id ?: return null
    if (reusedDescriptorId == newDescriptorId) return null
    return RunDashboardManagerImpl.getInstance(project).findService(reusedDescriptorId)
  }

  private fun guessServiceSuitableForDescriptor(descriptor: RunContentDescriptor): RunDashboardService? {
    val descriptorId = descriptor.id ?: return null
    val runDashboardManager = RunDashboardManagerImpl.getInstance(project)
    val settings = runDashboardManager.findSettings(descriptorId) ?: return null
    val services = runDashboardManager.getServices(settings) ?: return null

    val service = services.find { it.descriptorId == descriptorId }
    if (service != null) {
      return service
    }

    return services.find { it.descriptor?.processHandler?.isProcessTerminated ?: true }
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

  private fun disposeLuxForComponent(service: RunDashboardService, newDescriptor: RunContentDescriptor): RegisteredComponent? {
    val serviceId = service.uuid
    val registeredComponent = registeredComponents.remove(serviceId)

    val oldDescriptorId = service.descriptorId
    val newDescriptorId = newDescriptor.id
    if (oldDescriptorId != null && oldDescriptorId != newDescriptorId) {
      RunDashboardManagerImpl.getInstance(project).updateServiceRunContentDescriptor(oldDescriptorId, newDescriptorId)
    }
    if (registeredComponent == null) {
      return null
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
