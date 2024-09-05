// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.rpc.server

import fleet.rpc.RemoteApi
import fleet.rpc.RemoteApiDescriptor
import fleet.rpc.core.InstanceId
import kotlin.reflect.KClass

data class ServiceImplementation(val remoteApiDescriptor: RemoteApiDescriptor<*>, val instance: RemoteApi<*>)

interface RpcServiceLocator {
  fun resolve(serviceId: InstanceId): ServiceImplementation?
}

fun RpcServiceLocator.then(second: RpcServiceLocator): RpcServiceLocator {
  val first = this
  return object : RpcServiceLocator {
    override fun resolve(serviceId: InstanceId): ServiceImplementation? {
      return first.resolve(serviceId) ?: second.resolve(serviceId)
    }
  }
}

class MapServiceLocator(val map: Map<InstanceId, ServiceImplementation>) : RpcServiceLocator {
  override fun resolve(serviceId: InstanceId): ServiceImplementation? = map[serviceId]
}
