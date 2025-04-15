// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.rpc.server

import fleet.rpc.client.IRpcClient
import fleet.rpc.client.RpcInterceptor
import fleet.rpc.client.promisingRpcClient
import fleet.rpc.client.rpcClient
import fleet.rpc.core.Transport
import fleet.rpc.core.TransportMessage
import fleet.util.UID
import fleet.util.async.*
import fleet.util.channels.channels
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

fun RequestDispatcher.directRpcClient(
  interceptor: RpcInterceptor,
  abortOnError: Boolean,
): Resource<IRpcClient> =
  resource { cc ->
    val (dispatcherSend, clientReceive) = channels<TransportMessage>(Channel.BUFFERED)
    val (clientSend, dispatcherReceive) = channels<TransportMessage>(Channel.BUFFERED)
    val origin = UID.random()
    launch {
      handleConnection(route = origin,
                       endpoint = EndpointKind.Client,
                       send = dispatcherSend,
                       receive = dispatcherReceive,
                       presentableName = "directRpcClient")
    }.use {
      rpcClient(transport = Transport(outgoing = clientSend, incoming = clientReceive),
                origin = origin,
                requestInterceptor = interceptor,
                abortOnError = abortOnError) { rpcClient ->
        cc(rpcClient)
      }
    }
  }.span("directRpcClient").onContext(CoroutineName("directRpcClient"))

suspend fun RequestDispatcher.withDirectRpcClient(
  interceptor: RpcInterceptor,
  abortOnError: Boolean,
  body: suspend CoroutineScope.(IRpcClient) -> Unit,
) {
  directRpcClient(interceptor, abortOnError)
    .async()
    .use { rpcClientDeferred ->
      body(promisingRpcClient(rpcClientDeferred))
    }
}
