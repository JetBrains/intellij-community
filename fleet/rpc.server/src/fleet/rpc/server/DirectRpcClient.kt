// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.rpc.server

import fleet.rpc.client.IRpcClient
import fleet.rpc.client.RpcInterceptor
import fleet.rpc.client.promisingRpcClient
import fleet.rpc.client.rpcClient
import fleet.rpc.core.Serialization
import fleet.rpc.core.Transport
import fleet.rpc.core.TransportMessage
import fleet.tracing.spannedScope
import fleet.util.UID
import fleet.util.async.async
import fleet.util.async.resource
import fleet.util.async.use
import fleet.util.channels.channels
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

suspend fun RequestDispatcher.withDirectRpcClient(serialization: () -> Serialization,
                                                  interceptor: RpcInterceptor,
                                                  body: suspend CoroutineScope.(IRpcClient) -> Unit) {
  spannedScope("withDirectRpcClient") {
    val (dispatcherSend, clientReceive) = channels<TransportMessage>(Channel.BUFFERED)
    val (clientSend, dispatcherReceive) = channels<TransportMessage>(Channel.BUFFERED)
    val origin = UID.random()
    resource<IRpcClient> { continuation ->
      launch {
        handleConnection(route = origin,
                         endpoint = EndpointKind.Client,
                         send = dispatcherSend,
                         receive = dispatcherReceive,
                         presentableName = "directRpcClient")
      }.use {
        rpcClient(transport = Transport(outgoing = clientSend, incoming = clientReceive),
                  serialization = serialization,
                  origin = origin,
                  requestInterceptor = interceptor) { rpcClient ->
          continuation(rpcClient)
        }
      }
    }.async().use { rpcClientDeferred ->
      body(promisingRpcClient(rpcClientDeferred))
    }
  }
}
