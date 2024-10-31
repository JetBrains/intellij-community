// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.rpc.client

import fleet.rpc.RemoteApi
import fleet.rpc.RemoteApiDescriptor
import fleet.rpc.client.proxy.*
import fleet.rpc.core.*
import fleet.util.UID
import fleet.util.async.DelayStrategy
import fleet.util.logging.logger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.annotations.ApiStatus.Internal
import kotlin.coroutines.CoroutineContext

class FleetClient private constructor(
  val connectionStatus: StateFlow<ConnectionStatus<IRpcClient>>,
  val stats: MutableStateFlow<TransportStats>,
  private val job: Job,
) : CoroutineContext.Element {

  @Volatile
  private var poison: CancellationException? = null

  @Internal
  val invocationHandlerFactory: InvocationHandlerFactory<ProxyClosure> =
    reconnectingRpcClient(connectionStatus)
      .asHandlerFactory()
      .poisoned { poison }
      .tracing()

  private val serviceProxy: ServiceProxy =
    serviceProxy(invocationHandlerFactory).caching()

  fun asServiceProxy(): ServiceProxy = serviceProxy

  @Deprecated("Please use withFleetClient instead")
  suspend fun terminate(cause: CancellationException? = null) {
    poison = cause ?: CancellationException()
    job.cancel(cause)
    job.join()
  }

  companion object : CoroutineContext.Key<FleetClient> {
    val logger = logger<FleetClient>()

    // used in IJ iirc
    @Deprecated("Please use withFleetClient instead")
    fun create(scope: CoroutineScope,
               clientId: ClientId,
               transportFactory: FleetTransportFactory,
               delayStrategy: DelayStrategy = Exponential,
               requestInterceptor: RpcInterceptor = RpcInterceptor): FleetClient {
      val stats = MutableStateFlow(TransportStats())

      val (clientJob, connectionStatus) = scope.connectionLoop("FleetClient connection", delayStrategy) {
        val connected = CompletableDeferred<IRpcClient>()
        val parentScope = this
        launch {
          transportFactory.connect(stats) { transport ->
            rpcClient(transport, clientId.uid, requestInterceptor) { rpcClient ->
              connected.complete(rpcClient)
              awaitCancellation()
            }
          }
        }.invokeOnCompletion { cause ->
          if (cause != null) {
            connected.completeExceptionally(cause)
            // performed by trained professionals, don't try this at home
            // propagate the error to connectionLoop, so it logs something
            parentScope.cancel("Transport job finished", cause)
          }
          else {
            connected.completeExceptionally(RuntimeException("no cause given"))
          }
        }
        connected.await()
      }

      return FleetClient(connectionStatus, stats, clientJob)
    }
  }

  override val key: CoroutineContext.Key<*>
    get() = FleetClient
}

fun <A : RemoteApi<*>> FleetClient.proxy(remoteApiDescriptor: RemoteApiDescriptor<A>, route: UID, serviceId: InstanceId): A =
  asServiceProxy().proxy(remoteApiDescriptor, route, serviceId)

suspend fun withFleetClient(clientId: ClientId,
                            transportFactory: FleetTransportFactory,
                            delayStrategy: DelayStrategy = Exponential,
                            requestInterceptor: RpcInterceptor = RpcInterceptor,
                            body: suspend CoroutineScope.(FleetClient) -> Unit) {
  coroutineScope {
    @Suppress("DEPRECATION")
    val fleetClient = FleetClient.create(this,
                                         clientId = clientId,
                                         transportFactory = transportFactory,
                                         delayStrategy = delayStrategy,
                                         requestInterceptor = requestInterceptor)
    try {
      withContext(fleetClient) { body(fleetClient) }
    }
    finally {
      FleetClient.logger.info { "terminating fleet client" }
      @Suppress("DEPRECATION")
      fleetClient.terminate()
    }
  }
}
