// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.rpc.server

import fleet.rpc.RemoteApi
import fleet.rpc.RemoteApiDescriptor
import fleet.rpc.core.InstanceId
import kotlinx.coroutines.CoroutineScope

data class ServiceImplementation(
  val remoteApiDescriptor: RemoteApiDescriptor<*>,
  val instance: RemoteApi<*>,
  /**
   * All requests to the [instance] will be launched on this scope.
   *
   * Some implementations of RpcServiceLocator allow for dynamic registration/unregistration of services,
   * thus the interface must allow to control the lifetime of the associated coroutines.
   *
   * Falls back to the default supervised scope of RpcServiceExecutor if null.
   */
  val serviceScope: CoroutineScope?,
)

fun interface RpcServiceLocator {
  fun resolve(serviceId: InstanceId): ServiceImplementation?
}

class MapServiceLocator(val map: Map<InstanceId, ServiceImplementation>) : RpcServiceLocator {
  override fun resolve(serviceId: InstanceId): ServiceImplementation? = map[serviceId]
}