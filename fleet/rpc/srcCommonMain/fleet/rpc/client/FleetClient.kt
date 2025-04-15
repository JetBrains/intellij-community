// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.rpc.client

import fleet.rpc.RemoteApi
import fleet.rpc.RemoteApiDescriptor
import fleet.rpc.client.proxy.*
import fleet.rpc.core.*
import fleet.util.UID
import fleet.util.async.DelayStrategy
import fleet.util.async.Resource
import fleet.util.async.onContext
import fleet.util.async.resource
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.annotations.ApiStatus.Internal
import kotlin.coroutines.CoroutineContext
import kotlin.jvm.Volatile

class FleetClient internal constructor(
  val connectionStatus: StateFlow<ConnectionStatus<IRpcClient>>,
  val stats: MutableStateFlow<TransportStats>,
) : CoroutineContext.Element {
  companion object : CoroutineContext.Key<FleetClient>

  override val key: CoroutineContext.Key<*>
    get() = FleetClient

  @Volatile
  private var poison: CancellationException? = null

  @Internal
  val invocationHandlerFactory: InvocationHandlerFactory<ProxyClosure> =
    reconnectingRpcClient(connectionStatus)
      .asHandlerFactory()
      .poisoned { poison?.let { RuntimeException("RequestQueue is terminated", it) } }
      .tracing()

  private val serviceProxy: ServiceProxy =
    serviceProxy(invocationHandlerFactory).caching()

  fun asServiceProxy(): ServiceProxy = serviceProxy

  internal fun poison() {
    poison = CancellationException("Client was terminated")
  }
}

fun <A : RemoteApi<*>> FleetClient.proxy(remoteApiDescriptor: RemoteApiDescriptor<A>, route: UID, serviceId: InstanceId): A =
  asServiceProxy().proxy(remoteApiDescriptor, route, serviceId)

fun fleetClient(
  clientId: ClientId,
  transportFactory: FleetTransportFactory,
  abortOnError: Boolean,
  delayStrategy: DelayStrategy = Exponential,
  requestInterceptor: RpcInterceptor = RpcInterceptor,
): Resource<FleetClient> =
  resource { cc ->
    val stats = MutableStateFlow(TransportStats())
    connectionLoop<IRpcClient>(delayStrategy) { c ->
      transportFactory.connect(stats) { transport ->
        rpcClient(transport, clientId.uid, requestInterceptor, abortOnError) { rpcClient ->
          c(rpcClient)
        }
      }
    }.use { connectionStatus ->
      val fl = FleetClient(connectionStatus, stats)
      try {
        cc(fl)
      }
      finally {
        fl.poison()
      }
    }
  }.onContext(CoroutineName("fleetClient"))

@Deprecated("the only difference with fleetClient() is that this one puts in on coroutineContext. you can do it yourself. but better don't")
suspend fun withFleetClient(
  clientId: ClientId,
  transportFactory: FleetTransportFactory,
  abortOnError: Boolean,
  delayStrategy: DelayStrategy = Exponential,
  requestInterceptor: RpcInterceptor = RpcInterceptor,
  body: suspend CoroutineScope.(FleetClient) -> Unit,
) {
  fleetClient(clientId, transportFactory, abortOnError, delayStrategy, requestInterceptor).use { fleetClient ->
    withContext(fleetClient) {
      body(fleetClient)
    }
  }
}