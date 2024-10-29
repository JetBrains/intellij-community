// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.rpc.backend.impl

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
import kotlin.collections.set

internal class RemoteApiRegistry(coroutineScope: CoroutineScope) : RemoteApiProviderService, RpcServiceLocator {

  private val remoteApis = ConcurrentHashMap<String, ServiceImplementation>()
  private val visitedEPs = ContainerUtil.createConcurrentWeakKeyWeakValueMap<RemoteApiProvider, Unit>()

  private val registeringSink = object : RemoteApiProvider.Sink {
    override fun <T : RemoteApi<Unit>> remoteApi(descriptor: RemoteApiDescriptor<T>, implementation: () -> T) {
      remoteApis[descriptor.getApiFqn()] = ServiceImplementation(descriptor, implementation())
    }
  }

  private val unregisteringSink = object : RemoteApiProvider.Sink {
    override fun <T : RemoteApi<Unit>> remoteApi(descriptor: RemoteApiDescriptor<T>, implementation: () -> T) {
      remoteApis.remove(descriptor.getApiFqn())
    }
  }

  init {
    EP_NAME.addExtensionPointListener(coroutineScope, object : ExtensionPointListener<RemoteApiProvider> {
      override fun extensionAdded(extension: RemoteApiProvider, pluginDescriptor: PluginDescriptor) {
        if (visitedEPs.putIfAbsent(extension, Unit) == null) {
          with(extension) {
            registeringSink.remoteApis()
          }
        }
      }

      override fun extensionRemoved(extension: RemoteApiProvider, pluginDescriptor: PluginDescriptor) {
        visitedEPs.remove(extension)
        synchronized(this) {
          with(extension) {
            unregisteringSink.remoteApis()
          }
        }
      }
    })
    for (extension in EP_NAME.extensionList) {
      if (visitedEPs.putIfAbsent(extension, Unit) == null) {
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

  override fun resolve(serviceId: InstanceId): ServiceImplementation {
    return remoteApis[serviceId.id]
           ?: throw IllegalStateException("No remote API found for $serviceId")
  }
}
