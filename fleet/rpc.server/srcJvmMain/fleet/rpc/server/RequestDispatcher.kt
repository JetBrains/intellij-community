// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.rpc.server

import fleet.reporting.shared.tracing.spannedScope
import fleet.rpc.core.TransportMessage
import fleet.util.UID
import fleet.util.async.coroutineNameAppended
import fleet.util.channels.channels
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel

interface RequestDispatcher {
  suspend fun handleConnection(
    route: UID,
    endpoint: EndpointKind,
    presentableName: String? = null,
    send: SendChannel<TransportMessage>,
    receive: ReceiveChannel<TransportMessage>,
  )
}

suspend fun RequestDispatcher.serveRpc(
  route: UID,
  services: RpcServiceLocator,
  interceptor: RpcExecutorMiddleware = RpcExecutorMiddleware,
) {
  val dispatcher = this
  spannedScope("serveRpc") {
    val (dispatcherSend, executorReceive) = channels<TransportMessage>(Channel.BUFFERED)
    val (executorSend, dispatcherReceive) = channels<TransportMessage>(Channel.BUFFERED)
    launch {
      dispatcher.handleConnection(route = route,
                                  endpoint = EndpointKind.Provider,
                                  send = dispatcherSend,
                                  receive = dispatcherReceive)
    }
    withContext(coroutineNameAppended("Serving RPC as provider ${route}")) {
      RpcExecutor.serve(services = services,
                        sendChannel = executorSend,
                        receiveChannel = executorReceive,
                        rpcInterceptor = interceptor,
                        route = route)
    }
  }
}
