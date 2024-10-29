// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.rpc.client.proxy

import fleet.rpc.RemoteApi
import fleet.rpc.RemoteApiDescriptor
import fleet.rpc.client.IRpcClient
import fleet.rpc.client.asHandlerFactory
import fleet.rpc.core.InstanceId
import fleet.util.UID
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

interface ServiceProxy {
  fun <A : RemoteApi<*>> proxy(remoteApiDescriptor: RemoteApiDescriptor<A>, route: UID, instanceId: InstanceId): A

  class ContextElement internal constructor(val serviceProxy: ServiceProxy) : CoroutineContext.Element {
    companion object : CoroutineContext.Key<ContextElement>

    override val key: CoroutineContext.Key<*>
      get() = ContextElement
  }
}

fun ServiceProxy.asContextElement(): ServiceProxy.ContextElement =
  ServiceProxy.ContextElement(this)

suspend fun requireServiceProxy(): ServiceProxy =
  requireNotNull(coroutineContext[ServiceProxy.ContextElement]) { "ServiceProxy is not on context" }.serviceProxy

fun serviceProxy(handlerFactory: InvocationHandlerFactory<ProxyClosure>): ServiceProxy =
  object : ServiceProxy {
    override fun <A : RemoteApi<*>> proxy(
      remoteApiDescriptor: RemoteApiDescriptor<A>,
      route: UID,
      instanceId: InstanceId
    ): A = suspendProxy(remoteApiDescriptor, handlerFactory.handler(ProxyClosure(route, instanceId)))
  }

fun serviceProxy(rpcClient: IRpcClient): ServiceProxy =
  serviceProxy(rpcClient.asHandlerFactory().tracing())

data class ProxyClosure(
  val route: UID,
  val instanceId: InstanceId,
)
