// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.rpc.server

import fleet.rpc.core.Serialization
import fleet.rpc.core.TransportMessage
import fleet.tracing.spannedScope
import fleet.util.UID
import fleet.util.async.coroutineNameAppended
import fleet.util.channels.channels
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel

enum class EndpointKind {
  Client,
  Provider
}

interface RequestDispatcher {
  suspend fun handleConnection(route: UID,
                               endpoint: EndpointKind,
                               presentableName: String? = null,
                               send: SendChannel<TransportMessage>,
                               receive: ReceiveChannel<TransportMessage>)
}

suspend fun RequestDispatcher.serveRpc(route: UID,
                                       json: () -> Serialization,
                                       services: RpcServiceLocator,
                                       interceptor: RpcExecutorMiddleware = RpcExecutorMiddleware) {
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
      RpcExecutor.serve(json = json,
                        services = services,
                        sendChannel = executorSend,
                        receiveChannel = executorReceive,
                        rpcInterceptor = interceptor,
                        route = route)
    }
  }
}

private data class Handle<out T>(private val deferred: Deferred<T>, private val job: Job) {
  suspend fun await(): T = deferred.await()
  suspend fun join(): Unit = job.join()
  fun cancel(cause: CancellationException?): Unit = job.cancel(cause)
}

private fun <T> CoroutineScope.handle(body: suspend CoroutineScope.(suspend (T) -> Unit) -> Unit): Handle<T> {
  val deferred = CompletableDeferred<T>()
  val job = launch(start = CoroutineStart.ATOMIC) {
    body { t ->
      check(deferred.complete(t)) { "Subsequent invocations make no sense" }
      awaitCancellation()
    }
  }.apply {
    invokeOnCompletion { cause ->
      if (cause != null) {
        deferred.completeExceptionally(cause)
      }
      else {
        deferred.completeExceptionally(RuntimeException("job has finished"))
      }
    }
  }
  return Handle(deferred, job)
}

