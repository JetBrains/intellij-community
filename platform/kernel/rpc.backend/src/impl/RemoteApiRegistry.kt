// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.rpc.backend.impl

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.extensions.ExtensionPointListener
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.platform.rpc.RemoteApiProviderService
import com.intellij.platform.rpc.backend.RemoteApiProvider
import com.intellij.platform.rpc.backend.RemoteApiProvider.Companion.EP_NAME
import com.intellij.platform.rpc.backend.RemoteApiRegistration
import com.intellij.util.containers.ContainerUtil
import fleet.rpc.RemoteApi
import fleet.rpc.RemoteApiDescriptor
import fleet.rpc.core.InstanceId
import fleet.rpc.server.RpcServiceLocator
import fleet.rpc.server.ServiceImplementation
import kotlinx.coroutines.CoroutineScope
import java.util.concurrent.ConcurrentHashMap

internal class RemoteApiRegistry(coroutineScope: CoroutineScope) : RemoteApiProviderService, RpcServiceLocator {

  private val remoteApis = ConcurrentHashMap<String, ServiceImplementation>()
  private val visitedEPs = ContainerUtil.createConcurrentWeakKeyWeakValueMap<RemoteApiProvider, Unit>()

  private fun registerRemoteApi(apiDescriptor: RemoteApiDescriptor<*>, apiImplementation: RemoteApi<*>) {
    val apiFqn = apiDescriptor.getApiFqn()
    val serviceImplementation = ServiceImplementation(apiDescriptor, apiImplementation, serviceScope = null)

    val previous = remoteApis.putIfAbsent(apiFqn, serviceImplementation)
    if (previous != null) {
      LOG.error(
        "Remote API '$apiFqn' is already registered. Each remote api must be registered exactly once: " +
        "either via the 'com.intellij.platform.rpc.backend.remoteApi' extension point or a " +
        "'${RemoteApiProvider::class.java.simpleName}', not both and not more than once."
      )
    }
  }

  private fun unregisterRemoteApi(descriptor: RemoteApiDescriptor<*>) {
    LOG.debug("Unregistering remote api ${descriptor.getApiFqn()} - $descriptor")
    remoteApis.remove(descriptor.getApiFqn())
  }

  private val registeringSink = object : RemoteApiProvider.Sink {
    override fun <T : RemoteApi<Unit>> remoteApi(descriptor: RemoteApiDescriptor<T>, implementation: () -> T) {
      registerRemoteApi(
        apiDescriptor = descriptor,
        apiImplementation = implementation()
      )
    }
  }

  private val unregisteringSink = object : RemoteApiProvider.Sink {
    override fun <T : RemoteApi<Unit>> remoteApi(descriptor: RemoteApiDescriptor<T>, implementation: () -> T) {
      unregisterRemoteApi(descriptor)
    }
  }

  init {
    EP_NAME.addExtensionPointListener(coroutineScope, object : ExtensionPointListener<RemoteApiProvider> {
      override fun extensionAdded(extension: RemoteApiProvider, pluginDescriptor: PluginDescriptor) {
        if (visitedEPs.putIfAbsent(extension, Unit) == null) {
          LOG.debug("A new remote api provider has been added - $extension")
          with(extension) {
            registeringSink.remoteApis()
          }
        }
      }

      override fun extensionRemoved(extension: RemoteApiProvider, pluginDescriptor: PluginDescriptor) {
        visitedEPs.remove(extension)
        LOG.debug("Remote api provider has been removed - $extension")
        synchronized(this) {
          with(extension) {
            unregisteringSink.remoteApis()
          }
        }
      }
    })
    for (extension in EP_NAME.extensionList) {
      if (visitedEPs.putIfAbsent(extension, Unit) == null) {
        LOG.debug("Processing remote api provider extension - $extension")
        with(extension) {
          registeringSink.remoteApis()
        }
      }
    }

    RemoteApiRegistration.EP_NAME.addExtensionPointListener(coroutineScope, object : ExtensionPointListener<RemoteApiRegistration> {
      override fun extensionAdded(extension: RemoteApiRegistration, pluginDescriptor: PluginDescriptor) {
        register(extension)
      }

      override fun extensionRemoved(extension: RemoteApiRegistration, pluginDescriptor: PluginDescriptor) {
        unregister(extension)
      }
    })
    for (extension in RemoteApiRegistration.EP_NAME.extensionList) {
      register(extension)
    }
  }

  private fun register(registration: RemoteApiRegistration) {
    registerRemoteApi(
      apiDescriptor = registration.loadApiDescriptor(),
      apiImplementation = registration.createImplementation()
    )
  }

  private fun unregister(registration: RemoteApiRegistration) {
    unregisterRemoteApi(descriptor = registration.loadApiDescriptor())
  }

  override suspend fun <T : RemoteApi<Unit>> resolve(descriptor: RemoteApiDescriptor<T>): T {
    @Suppress("UNCHECKED_CAST")
    return remoteApis[descriptor.getApiFqn()]?.instance as? T
           ?: throw IllegalStateException("No remote API found for $descriptor")
  }

  override fun resolve(serviceId: InstanceId): ServiceImplementation? {
    return remoteApis[serviceId.id].also {
      if (it == null) {
        LOG.debug("No remote API found for service ID: ${serviceId.id}")
        LOG.trace { "Available remote APIs: ${remoteApis.keys.joinToString("\n\t")}" }
      }
    }
  }

  override fun listRegisteredApis(): List<String> {
    return remoteApis.keys.toList()
  }

  override fun isServiceOperational(): Boolean {
    return true
  }

  companion object {
    private val LOG = logger<RemoteApiRegistry>()
  }
}
