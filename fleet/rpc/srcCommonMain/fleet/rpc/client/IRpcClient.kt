// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.rpc.client

import fleet.rpc.RemoteApiDescriptor
import fleet.rpc.RpcSignature
import fleet.rpc.client.proxy.InvocationHandlerFactory
import fleet.rpc.client.proxy.ProxyClosure
import fleet.rpc.client.proxy.SuspendInvocationHandler
import fleet.rpc.core.ConnectionStatus
import fleet.rpc.core.InstanceId
import fleet.rpc.core.PrefetchStrategy
import fleet.util.UID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext
import kotlin.concurrent.Volatile

data class Call(val route: UID,
                val service: InstanceId,
                val signature: RpcSignature,
                val arguments: List<Any?>) {
  fun display(): String = "route=$route, $service/${signature.methodName}(${signature.parameters.map { it.parameterName }.joinToString(", ")})"
}

internal data class RpcStrategyContextElement(val awaitConnection: Boolean = true,
                                              val prefetchStrategy: PrefetchStrategy = PrefetchStrategy.Default) : CoroutineContext.Element {
  companion object : CoroutineContext.Key<RpcStrategyContextElement>

  override val key: CoroutineContext.Key<*> get() = RpcStrategyContextElement
}

suspend fun <T> withoutAwaitingForReconnect(body: suspend CoroutineScope.() -> T): T {
  val strategy = coroutineContext[RpcStrategyContextElement]?.copy(awaitConnection = false)
                 ?: RpcStrategyContextElement(awaitConnection = false)
  return withContext(strategy) {
    body()
  }
}

suspend fun <T> withPrefetchStrategy(prefetchStrategy: PrefetchStrategy, body: suspend CoroutineScope.() -> T): T {
  val strategy = coroutineContext[RpcStrategyContextElement]?.copy(prefetchStrategy = prefetchStrategy)
                 ?: RpcStrategyContextElement(prefetchStrategy = prefetchStrategy)
  return withContext(strategy) {
    body()
  }
}

interface IRpcClient {
  suspend fun call(call: Call, publish: (SuspendInvocationHandler.CallResult) -> Unit)
}

fun promisingRpcClient(promise: Deferred<IRpcClient>): IRpcClient {
  return object : IRpcClient {
    override suspend fun call(call: Call, publish: (SuspendInvocationHandler.CallResult) -> Unit) {
      return promise.await().call(call, publish)
    }
  }
}

fun IRpcClient.asHandlerFactory(): InvocationHandlerFactory<ProxyClosure> =
  object : InvocationHandlerFactory<ProxyClosure> {
    override fun handler(arg: ProxyClosure): SuspendInvocationHandler {
      return object : SuspendInvocationHandler {
        override suspend fun call(remoteApiDescriptor: RemoteApiDescriptor<*>,
                                  method: String,
                                  args: List<Any?>,
                                  publish: (SuspendInvocationHandler.CallResult) -> Unit) {
          call(
            Call(
              route = arg.route,
              service = arg.instanceId,
              signature = remoteApiDescriptor.getSignature(method),
              arguments = args,
            ),
            publish,
          )
        }
      }
    }
  }

internal fun reconnectingRpcClient(attempts: StateFlow<ConnectionStatus<IRpcClient>>): IRpcClient {
  return object : IRpcClient {
    @Volatile
    var mayHaveCalls = false

    @Volatile
    var disconnectedClient: IRpcClient? = null

    override suspend fun call(call: Call, publish: (SuspendInvocationHandler.CallResult) -> Unit) {
      withTimeoutOrNull(RpcClient.RPC_TIMEOUT) {
        mayHaveCalls = true
        val awaitConnection = coroutineContext[RpcStrategyContextElement]?.awaitConnection ?: true
        val client = if (awaitConnection) {
          attempts.mapNotNull { (it as? ConnectionStatus.Connected)?.value }.first { it != disconnectedClient }
        }
        else {
          (attempts.value as? ConnectionStatus.Connected)?.value
        }

        if (client == null) {
          throw RpcClientDisconnectedException("Connection is not available. Current rpc strategy opted out waiting for reconnect.", null)
        }
        try {
          client.call(call, publish)
          disconnectedClient = null
        }
        catch (x: RpcClientDisconnectedException) {
          disconnectedClient = client
          throw x
        }
        Unit
      } ?: throw RpcTimeoutException("Client did not connect after ${RpcClient.RPC_TIMEOUT}", null)
    }
  }
}