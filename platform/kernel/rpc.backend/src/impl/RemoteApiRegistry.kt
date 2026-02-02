// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.rpc.backend.impl

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.extensions.ExtensionPointListener
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.platform.rpc.RemoteApiProviderService
import com.intellij.platform.rpc.backend.RemoteApiProvider
import com.intellij.platform.rpc.backend.RemoteApiProvider.Companion.EP_NAME
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

  private val registeringSink = object : RemoteApiProvider.Sink {
    override fun <T : RemoteApi<Unit>> remoteApi(descriptor: RemoteApiDescriptor<T>, implementation: () -> T) {
      LOG.debug("Registering remote api ${descriptor.getApiFqn()} - $descriptor")
      remoteApis[descriptor.getApiFqn()] = ServiceImplementation(descriptor, implementation(), null)
    }
  }

  private val unregisteringSink = object : RemoteApiProvider.Sink {
    override fun <T : RemoteApi<Unit>> remoteApi(descriptor: RemoteApiDescriptor<T>, implementation: () -> T) {
      LOG.debug("Unregistering remote api ${descriptor.getApiFqn()} - $descriptor")
      remoteApis.remove(descriptor.getApiFqn())
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

  companion object {
    private val LOG = logger<RemoteApiRegistry>()
  }
}
