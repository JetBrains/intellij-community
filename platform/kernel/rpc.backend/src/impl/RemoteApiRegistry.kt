// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.rpc.backend.impl

import com.intellij.openapi.extensions.ExtensionPointListener
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.platform.rpc.RemoteApiProviderService
import com.intellij.platform.rpc.backend.RemoteApiProvider
import com.intellij.platform.rpc.backend.RemoteApiProvider.Companion.EP_NAME
import com.intellij.util.containers.ContainerUtil
import fleet.rpc.RemoteApi
import fleet.rpc.core.InstanceId
import fleet.rpc.server.RpcServiceLocator
import fleet.rpc.server.ServiceImplementation
import kotlinx.coroutines.CoroutineScope
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass

internal class RemoteApiRegistry(coroutineScope: CoroutineScope) : RemoteApiProviderService, RpcServiceLocator {

  private val remoteApis = ConcurrentHashMap<InstanceId, ServiceImplementation>()
  private val visitedEPs = ContainerUtil.createConcurrentWeakKeyWeakValueMap<RemoteApiProvider, Unit>()

  init {
    EP_NAME.addExtensionPointListener(coroutineScope, object : ExtensionPointListener<RemoteApiProvider> {
      override fun extensionAdded(extension: RemoteApiProvider, pluginDescriptor: PluginDescriptor) {
        if (visitedEPs.putIfAbsent(extension, Unit) == null) {
          val apis = extension.getApis()
          for (api in apis) {
            remoteApis[api.klass.toInstanceId] = ServiceImplementation(api.klass, api.service())
          }
        }
      }

      override fun extensionRemoved(extension: RemoteApiProvider, pluginDescriptor: PluginDescriptor) {
        visitedEPs.remove(extension)
        val apis = extension.getApis()
        synchronized(this) {
          apis.forEach { api ->
            remoteApis.remove(api.klass.toInstanceId)
          }
        }
      }
    })
    EP_NAME.extensions.filter { visitedEPs.putIfAbsent(it, Unit) == null }.flatMap { it.getApis() }.forEach { api ->
      remoteApis[api.klass.toInstanceId] = ServiceImplementation(api.klass, api.service())
    }
  }

  override suspend fun <T : RemoteApi<Unit>> resolve(klass: KClass<T>): T {
    @Suppress("UNCHECKED_CAST")
    return remoteApis[klass.toInstanceId]?.instance as? T
           ?: throw IllegalStateException("No remote API found for $klass")
  }

  override fun resolve(serviceId: InstanceId): ServiceImplementation? {
    return remoteApis[serviceId]
  }
}

private val KClass<out RemoteApi<Unit>>.toInstanceId: InstanceId
  get() = InstanceId(this.qualifiedName!!)
