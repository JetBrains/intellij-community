// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.rpc.client

import fleet.rpc.RemoteApiDescriptor
import fleet.rpc.RemoteKind
import fleet.rpc.client.proxy.*
import fleet.rpc.core.*
import fleet.rpc.serializer
import fleet.util.UID
import fleet.util.async.resource
import fleet.util.async.use
import fleet.util.async.withSupervisor
import fleet.util.causeOfType
import fleet.util.channels.consumeAll
import fleet.util.channels.isFull
import fleet.util.logging.KLoggers
import kotlinx.collections.immutable.toPersistentSet
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.selects.whileSelect
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import fleet.multiplatform.shims.ConcurrentHashMap
import fleet.multiplatform.shims.newSingleThreadCoroutineDispatcher
import kotlin.coroutines.*

private data class OutgoingRequest(
  val route: UID,
  val token: RpcToken?,
  val call: RpcMessage.CallRequest,
  val continuation: CancellableContinuation<Any?>,
  val returnType: RemoteKind,
  val prefetchStrategy: PrefetchStrategy,
  val streamParameters: List<StreamDescriptor>,
)

private data class OngoingRequest(val request: OutgoingRequest)

@OptIn(ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class)
suspend fun <T> rpcClient(
  transport: Transport<TransportMessage>,
  origin: UID,
  requestInterceptor: RpcInterceptor = RpcInterceptor,
  abortOnError: Boolean,
  body: suspend CoroutineScope.(RpcClient) -> T,
): T =
  newSingleThreadCoroutineDispatcher("rpc-client-$origin").use { dispatcher ->
    withSupervisor { supervisor ->
      val client = RpcClient(coroutineScope = supervisor + CoroutineName("RpcScope"),
                             transport = transport,
                             origin = origin,
                             requestInterceptor = requestInterceptor)
      launch(start = CoroutineStart.ATOMIC, context = dispatcher + CoroutineName("RpcClient")) { client.work(abortOnError) }
        .use {
          body(client)
        }
    }
  }

class RpcClient internal constructor(
  private val coroutineScope: CoroutineScope,
  private val transport: Transport<TransportMessage>,
  private val origin: UID,
  private val requestInterceptor: RpcInterceptor,
) : IRpcClient {

  private val eventLoopChannel = Channel<(cause: Throwable?) -> Unit>(Channel.UNLIMITED) { element ->
    element.invoke(RpcClientDisconnectedException("Request channel closed", null))
  }

  private val requestsChannel = Channel<Pair<TransportMessage, RequestCompletionHandler?>>(Channel.UNLIMITED) { element ->
    element.second?.invoke(RpcClientDisconnectedException("Request channel closed", null))
  }

  private val grayList = ConcurrentHashMap<UID, CompletableDeferred<Unit>>()

  private val outgoingRpc = ConcurrentHashMap<UID, OngoingRequest>()
  private val completedRpc = ConcurrentHashMap<UID, TransferredResource>()
  private val streams = ConcurrentHashMap<UID, InternalStreamDescriptor>()
  private val remoteResources = ConcurrentHashMap<InstanceId, Set<Pair<InstanceId, RemoteResource>>>()
  private val resourceParents = ConcurrentHashMap<InstanceId, InstanceId>()

  private val remoteObjectFactory = this.asHandlerFactory().tracing()

  private fun <T : RemoteObject> remoteObject(remoteApiDescriptor: RemoteApiDescriptor<T>, path: String, route: UID): T =
    suspendProxy(remoteApiDescriptor, remoteObjectFactory.handler(ProxyClosure(
      route = route,
      instanceId = InstanceId(path)
    )))

  private fun <T : RemoteResource> remoteResource(remoteApiDescriptor: RemoteApiDescriptor<T>, instanceId: InstanceId, route: UID, parentService: InstanceId): T {
    val resource = suspendProxy(
      remoteApiDescriptor,
      remoteObjectFactory
        .poisoned {
          // Resource should still be registered
          if (resourceParents.containsKey(instanceId)) null
          else RemoteResourceConsumedException()
        }
        .handler(ProxyClosure(
          route = route,
          instanceId = instanceId
        ))
    )

    remoteResources.compute(parentService) { k, s -> s.orEmpty().toPersistentSet().add(instanceId to resource) }
    resourceParents.put(instanceId, parentService)

    return resource
  }

  companion object {
    internal val logger = KLoggers.logger(RpcClient::class)
    internal const val RPC_TIMEOUT = 60_000L
  }

  private sealed class Event {
    class Message(val message: TransportMessage) : Event()
    class Command(val command: (cause: Throwable?) -> Unit) : Event()
  }

  private suspend fun <T> ChannelResult<T>.receiveSuccess(f: suspend (T) -> Unit): Boolean {
    return when {
      this.isSuccess -> {
        f(this.getOrThrow())
        true
      }
      this.isClosed -> false
      this.isFailure -> {
        throw this.exceptionOrNull() ?: error("receive is a failure without exception")
      }
      else -> {
        error("unreachable")
      }
    }
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  internal suspend fun work(abortOnError: Boolean) {
    supervisorScope {
      val receiver = async(start = CoroutineStart.ATOMIC) {
        consumeAll(transport.incoming, eventLoopChannel) {
          val mergedIncomingAndTransport = flow {
            whileSelect {
              transport.incoming.onReceiveCatching { incoming ->
                incoming.receiveSuccess { emit(Event.Message(it)) }
              }
              eventLoopChannel.onReceiveCatching { outgoing ->
                outgoing.receiveSuccess { emit(Event.Command(it)) }
              }
            }
          }
          mergedIncomingAndTransport.collect { event ->
            // TODO do I need to modify telemetryContext here?
            //message.otelData()?.let { telemetryData ->
            //  OpenTelemetry.getGlobalPropagators().textMapPropagator.extract(Context.current(), telemetryData, TelemetryData.otelGetter).makeCurrent().use {
            //  }
            //}

            try {
              when (event) {
                is Event.Message -> {
                  logger.trace { "Received ${event.message}" }
                  when (val message = event.message) {
                    is TransportMessage.Envelope -> {
                      acceptMessage(message.parseMessage(), message.origin)
                    }
                    is TransportMessage.RouteClosed -> {
                      grayList.putIfAbsent(message.address, CompletableDeferred())
                      resumeWithRouteClosed(message.address)
                      // race: some new requests to this route may be enqueued at this point
                      // but this is ok, we will send them to request dispatcher and receive reject either way
                    }
                    is TransportMessage.RouteOpened -> {
                      grayList.remove(message.address)?.let {
                        logger.trace { "Removed ${message.address} from gray list" }
                        it.complete(Unit)
                      }
                    }
                  }
                }
                is Event.Command -> {
                  event.command(null)
                }
              }
            }
            catch (t: Throwable) {
              logger.error(t) { "Exception during processing incoming message" }
              if (abortOnError) {
                throw AssertionError("Exception during processing incoming message", t)
              }
            }
          }
        }
      }.apply {
        invokeOnCompletion { cause ->
          requestsChannel.close(cause)
        }
      }

      val sender = async(start = CoroutineStart.ATOMIC) {
        try {
          requestsChannel.consumeEach { (message, onSend) ->
            try {
              logger.trace { "Sending ${message}" }
              transport.outgoing.send(message)
            }
            catch (e: Throwable) {
              onSend?.invoke(e.causeOfType<TransportDisconnectedException>()?.let { RpcClientDisconnectedException(null, it) } ?: e)
              throw e
            }
            onSend?.invoke(null)
          }
        }
        catch (ignore: ClosedSendChannelException) {
        }
      }

      var cause: Throwable? = null
      try {
        sender.await()
        receiver.await()
      }
      catch (ex: Throwable) {
        cause = ex
      }
      finally {
        if (cause != null) {
          logger.debug(cause) { "Cancelling request queue" }
        }
        else {
          logger.debug { "Cancelling request queue normally" }
        }
        transport.outgoing.close()
        val ex = cause?.causeOfType<TransportDisconnectedException>()?.let { RpcClientDisconnectedException(null, it) }
                 ?: cause
                 ?: RpcClientDisconnectedException("Transport channel closed without cause", cause = null)
        resumeAllOngoingCallsWithThrowable(ex)
        requestsChannel.close(cause)
        throw ex
      }
    }
  }

  private fun executeCommand(f: RequestCompletionHandler) {
    val offered = eventLoopChannel.trySend(f)
    require(!offered.isFull) { "requestChannel overflown" }
    if (!offered.isSuccess) {
      val throwable = offered.exceptionOrNull()?.let { cause ->
        cause.causeOfType<TransportDisconnectedException>()?.let { RpcClientDisconnectedException(null, it) }
      } ?: RpcClientDisconnectedException("Failed to send, looks like RpcClient is shut down", offered.exceptionOrNull())
      f.invoke(throwable)
    }
  }

  private fun sendAsync(message: TransportMessage, completionHandler: RequestCompletionHandler? = null) {
    val offered = requestsChannel.trySend(message to completionHandler)
    require(!offered.isFull) { "requestChannel overflown" }
    if (!offered.isSuccess) {
      val throwable = offered.exceptionOrNull()?.let { cause ->
        cause.causeOfType<TransportDisconnectedException>()?.let { RpcClientDisconnectedException(null, it) }
      } ?: RpcClientDisconnectedException("Failed to send, looks like RpcClient is shut down", offered.exceptionOrNull())
      completionHandler?.invoke(throwable)
    }
  }

  private class TransferredResource(
    val streams: List<InternalStreamDescriptor>,
    val prefetchStrategy: PrefetchStrategy,
  )

  private fun disposeResponseResource(resource: TransferredResource) {
    resource.streams.forEach { stream ->
      unregisterStream(stream.uid)
      sendAsync(RpcMessage.StreamClosed(streamId = stream.uid).seal(stream.route, origin), null)
    }
  }

  private fun disposeLocalResource(instanceId: InstanceId, resource: RemoteResource) {
    remoteResources.remove(instanceId)?.forEach { (childId, childResource) ->
      resourceParents.remove(childId)
      disposeLocalResource(childId, childResource)
    }

    // Remove the current resource from parent, if not done already
    resourceParents.remove(instanceId)?.let { parentId ->
      remoteResources.compute(parentId) { _, set ->
        if (set != null) (set - (instanceId to resource)).ifEmpty { null } else null
      }
    }
  }

  private fun CoroutineScope.acceptMessage(message: RpcMessage, senderRoute: UID) {
    when (message) {
      is RpcMessage.CallResult -> {
        logger.trace { "Got CallResult: requestId = ${message.requestId}" }
        // TODO this value can contain send/receive channels that must be closed, we should not drop it on the ground
        outgoingRpc.remove(message.requestId)?.let { (rpc) ->
          try {
            val (returnResult, streams) = run {
              if (rpc.returnType is RemoteKind.RemoteObject) {
                val path = Json.decodeFromJsonElement(String.serializer(), message.result)
                val remoteApiDescriptor = rpc.returnType.descriptor as RemoteApiDescriptor<RemoteObject>
                return@run remoteObject(remoteApiDescriptor, path, rpc.route) to emptyList()
              }
              else if (rpc.returnType is RemoteKind.Resource) {
                val path = Json.decodeFromJsonElement(InstanceId.serializer(), message.result)
                val remoteApiDescriptor = rpc.returnType.descriptor as RemoteApiDescriptor<RemoteResource>
                val resource = resource {
                  val resource = remoteResource(remoteApiDescriptor, path, rpc.route, parentService = rpc.call.service)

                  try {
                    it(resource)
                  }
                  finally {
                    disposeLocalResource(path, resource)

                    // Dispose on the server side when done being used
                    sendAsync(RpcMessage.ResourceConsumed(path).seal(rpc.route, origin))
                  }
                }
                return@run resource to emptyList()
              }
              val (de, streamDescriptors) = withSerializationContext(rpc.call.displayName, rpc.token, this) {
                val kser = rpc.returnType.serializer(rpc.call.classMethodDisplayName())
                val json = rpcJsonImplementationDetail()
                json.decodeFromJsonElement(kser, message.result)
              }
              val internalDescriptors = registerStreams(streamDescriptors, rpc.route, rpc.prefetchStrategy)
              // we register streams immediately to catch messages
              // but we are not sure the continuation will be resumed successfully so let's postpone serving coroutines
              return@run de to internalDescriptors
            }
            completedRpc[message.requestId] = TransferredResource(
              streams = streams,
              prefetchStrategy = rpc.prefetchStrategy
            )
            interceptCallResult(rpc, message) { result ->
              result
                .onSuccess {
                  logger.trace { "Resuming ${message.requestId} isActive ${rpc.continuation.isActive}" }
                  rpc.continuation.resumeWith(Result.success(returnResult))
                }
                .onFailure { f ->
                  // todo: what should we do with channels?
                  // todo: what it returnType is Channel<Socket>, should we drain it first and close all sockets that have been sent already?
                  rpc.continuation.resumeWith(Result.failure(f))
                }
            }
          }
          catch (ex: Throwable) {
            rpc.continuation.resumeWith(Result.failure(ex))
          }
        }
      }

      is RpcMessage.StreamData -> {
        val stream = streams[message.streamId]
        if (stream != null) {
          when (stream) {
            is InternalStreamDescriptor.FromRemote -> {
              val (element, streamDescriptors) = withSerializationContext(stream.displayName, stream.token, this) {
                rpcJsonImplementationDetail().decodeFromJsonElement(stream.elementSerializer, message.data)
              }
              for (internalDescriptor in registerStreams(streamDescriptors, stream.route, stream.prefetchStrategy)) {
                serveStream(internalDescriptor, stream.prefetchStrategy)
              }
              val result = stream.bufferedChannel.trySend(InternalStreamMessage.Payload(element))
              // CancellationException means that user channel was cancelled; stream desc will be removed from `streams` map soon
              if (!result.isSuccess && result.exceptionOrNull() !is CancellationException) {
                throw IllegalStateException(result.exceptionOrNull())
              }
            }
            else -> {
              logger.error { "Received StreamData from remote, but ${message.streamId} is a ToRemote channel" }
            }
          }
        }
        else {
          // TODO this value can contain send/receive channels that must be closed
          logger.trace { "Received StreamData for unregistered stream ${message.streamId}" }
        }
      }

      is RpcMessage.StreamClosed -> {
        val desc = streams.remove(message.streamId)
        if (desc != null) {
          streamClosedByRemote(desc, message.error)
        }
        else {
          logger.trace { "Can't close stream (id=${message.streamId}) on RpcMessage.StreamClosed, not present in map" }
        }
      }
      is RpcMessage.StreamNext -> {
        streams[message.streamId]?.requireStreamToRemote()?.budget?.refill(message.count)
      }
      is RpcMessage.CallFailure -> {
        requestFailed(message.requestId, message.error)
      }
      is RpcMessage.StreamInit -> {
        if (streams[message.streamId] == null) {
          logger.trace { "received StreamInit for unregistered stream ${message.streamId}, will respond with StreamClosed" }
          sendAsync(RpcMessage.StreamClosed(message.streamId).seal(senderRoute, origin))
        }
      }
      else -> error("Unexpected message: $message")
    }
  }

  private fun resumeAllOngoingCallsWithThrowable(throwable: Throwable) {
    logger.debug(throwable) { "resumeAllOngoingCallsWithThrowable" }

    for (key in outgoingRpc.keys) {
      outgoingRpc.remove(key)?.let {
        logger.trace { "resumeAllOngoingCallsWithThrowable: resume request $key" }
        it.request.continuation.resumeWithException(throwable)
      }
    }
    streams.values.removeAll {
      it.closeStream(throwable)
      true
    }
    grayList.values.forEach { it.completeExceptionally(throwable) }
  }

  private fun resumeWithRouteClosed(route: UID) {
    val message = "Route $route closed"
    for ((key, value) in outgoingRpc) {
      if (value.request.route == route) {
        outgoingRpc.remove(key)?.let {
          it.request.continuation.resumeWithException(RouteClosedException(route, rpcCallFailureMessage(it.request.call, message)))
        }
      }
    }
    streams.values.removeAll {
      if (it.route == route) {
        it.closeStream(RouteClosedException(route, rpcStreamFailureMessage(it.displayName, message)))
        true
      }
      else {
        false
      }
    }
  }

  private fun requestFailed(requestId: UID, error: FailureInfo) {
    logger.trace { "Removing failed request (requestId = ${requestId}) from queue because of the error: $error" }
    outgoingRpc.remove(requestId)?.let { (rpc) ->

      val exception = when {
        error.conflict != null -> AssumptionsViolatedException(error.conflict)
        error.serviceNotReady != null -> RpcServiceNotReady(rpc.call)
        error.unresolvedService != null -> UnresolvedServiceException(rpc.call.service)
        else -> RpcException.callFailed(rpc.call, error)
      }

      rpc.continuation.resumeWith(Result.failure(exception))
    }
    // we should not close channels on timeout, request doesn't own channels,
    // they might be read by other coroutines already and it might be unexpected to cancel them
    // we consider that as the least surprising behaviour
    // if you decide to go another way, please consider case when you have to close channel of channels of channels
  }

  private fun requestCanceledByClient(requestId: UID, cause: Throwable) {
    logger.trace(cause) { "Removing cancelled request ${requestId} from queue" }
    val req = outgoingRpc.remove(requestId)
    when {
      req != null -> {
        val (rpc) = req
        try {
          val cancelMessage = RpcMessage.CancelCall(requestId = requestId)
            .seal(destination = rpc.route, origin = origin)
          sendAsync(cancelMessage)
        }
        catch (suppressed: Exception) {
          if (cause != suppressed) cause.addSuppressed(suppressed)
        }
        //rpc.continuation.resumeWith(Result.failure(cause))
      }
      else -> {
        logger.trace { "Cannot cancel request $requestId, it is not registered" }
      }
    }
    completedRpc.remove(requestId)?.let { resource ->
      logger.trace { "Cancelling streams of lingering request $requestId" }
      disposeResponseResource(resource)
    } ?: run { logger.trace { "Cancelled request $requestId was not yet completed, nothing to cancel" } }
  }

  private fun streamClosedByRemote(desc: InternalStreamDescriptor, error: FailureInfo?) {
    val cause = error?.let { RpcException.streamFailed(desc.displayName, it) }
    when (desc) {
      is InternalStreamDescriptor.ToRemote -> {
        desc.channel.cancel(CancellationException("streamClosedByRemote", cause))
      }
      is InternalStreamDescriptor.FromRemote -> {
        val producerCancelled = error?.producerCancelled
        val causePrime = if (producerCancelled != null) {
          ProducerIsCancelledException(msg = rpcStreamFailureMessage(desc.displayName, error.message()), cause = null) as Throwable?
        }
        else {
          cause as Throwable? // without `as Throwable?` wasm compiles but throws on wasm compilation in the browser (same as in FL-32234)
        }
        desc.bufferedChannel.close(causePrime)
      }
    }
  }

  private fun unregisterStream(streamId: UID, err: () -> Throwable? = { null }) {
    streams.remove(streamId)?.let { desc ->
      logger.trace { "Unregistering stream (id=$streamId)" }
      desc.closeStream(err())
    }
  }

  private fun InternalStreamDescriptor.closeStream(cause: Throwable?) {
    logger.trace { "closeStream (id=${this.uid})" }
    when (this) {
      is InternalStreamDescriptor.FromRemote -> {
        bufferedChannel.close(cause)
        channel.close(cause)
      }
      is InternalStreamDescriptor.ToRemote -> {
        val cancellationException = (cause as? CancellationException) ?: cause?.let { CancellationException(it.message, it) }
        budget.cancel(cancellationException ?: CancellationException("The stream was cancelled"))
        channel.cancel(cancellationException)
      }
    }
  }

  private fun interceptCallResult(
    rpc: OutgoingRequest,
    message: RpcMessage.CallResult,
    continuation: (Result<Unit>) -> Unit,
  ) {
    suspend fun intercept() {
      requestInterceptor.interceptCallResult(rpc.call.displayName, message)
    }
    ::intercept.startCoroutine(Continuation(rpc.continuation.context, continuation))
  }

  override suspend fun call(call: Call, publish: (SuspendInvocationHandler.CallResult) -> Unit) {
    val requestId = UID.random()
    logger.trace { "executing call ${call.display()} with id $requestId" }
    val token = coroutineContext[RpcToken]
    val (serializedArguments, streamParameters) = run {
      val json = rpcJsonImplementationDetail()
      val triples = (call.arguments zip call.signature.parameters).map { (arg, parameterDescriptor) ->
        val parameterName = parameterDescriptor.parameterName
        val displayName = methodParamDisplayName(classMethodDisplayName(call.service.id, call.signature.methodName), parameterName)
        val (ser, streams) = withSerializationContext(displayName, token, coroutineScope) {
          val kser = parameterDescriptor.parameterKind.serializer(call.display())
          json.encodeToJsonElement(kser, arg)
        }
        Triple(parameterName, ser, streams)
      }
      triples.associate { (n, s) -> n to s } to triples.flatMap { (_, _, streams) -> streams }
    }
    val uninterceptedRequest = RpcMessage.CallRequest(requestId = requestId,
                                                      service = call.service,
                                                      method = call.signature.methodName,
                                                      args = serializedArguments)
    withTimeoutOrNull(RPC_TIMEOUT) {
      val callRequest = requestInterceptor.interceptCallRequest(uninterceptedRequest)
      logger.trace { "Interceptor completed for request ${callRequest}" }
      val rpcStrategy = coroutineContext[RpcStrategyContextElement] ?: RpcStrategyContextElement()
      if (rpcStrategy.awaitConnection) {
        logger.trace { "request $requestId, waiting for ${call.route} to become available" }
        grayList[call.route]?.await()
        logger.trace { "request $requestId, ${call.route} is available" }
      }
      else if (grayList.contains(call.route)) {
        throw RouteClosedException(call.route, rpcCallFailureMessage(callRequest, "Route ${call.route} closed"))
      }

      suspendCancellableCoroutine { cc ->
        val request = OutgoingRequest(route = call.route,
                                      call = callRequest,
                                      token = token,
                                      continuation = cc,
                                      returnType = call.signature.returnType,
                                      streamParameters = streamParameters,
                                      prefetchStrategy = rpcStrategy.prefetchStrategy)
        val resumeWithException = { cause: Throwable ->
          val exToResumeWith = cause.causeOfType<TransportDisconnectedException>()?.let { RpcClientDisconnectedException(null, it) }
                               ?: cause
          logger.trace(exToResumeWith) { "Failed to send request $requestId with exception, remove it from queue" }
          outgoingRpc.remove(requestId)?.let<OngoingRequest, Unit> { (r) ->
            for (stream in r.streamParameters) {
              unregisterStream(stream.uid) { exToResumeWith }
            }
            r.continuation.resumeWithException(exToResumeWith)
          }
        }
        executeCommand { cause ->
          if (cause == null) {
            logger.trace { "Register request ${request.call} in queue" }
            val previous = outgoingRpc.putIfAbsent(requestId, OngoingRequest(request))
            check(previous == null) { "Request with id $requestId is already present in the queue" }
            val streamDescriptors = registerStreams(request.streamParameters, request.route, rpcStrategy.prefetchStrategy)

            // Also dispose local resource issued from remote objects
            if (call.signature.methodName == "clientDispose") {
              remoteResources.remove(call.service)?.forEach { (instanceId, resource) ->
                disposeLocalResource(instanceId, resource)
              }
            }

            sendAsync(callRequest.seal(destination = request.route, origin = origin)) { cause ->
              if (cause == null) {
                logger.trace { "Request sent ${request.call}" }
                // register cancellation handler only after the request is enqueued or we can end up sending CancelCall before CallRequest
                request.continuation.invokeOnCancellation { c ->
                  if (c != null) {
                    // be careful, invokeOnCancellation is invoked concurrently to the main event loop
                    executeCommand { ex ->
                      if (ex == null) {
                        requestCanceledByClient(requestId, c)
                      }
                    }
                  }
                }
                for (internalDescriptor in streamDescriptors) {
                  serveStream(internalDescriptor, rpcStrategy.prefetchStrategy)
                }
              }
              else {
                resumeWithException(cause)
              }
            }
          }
          else {
            val exToResumeWith = cause.causeOfType<TransportDisconnectedException>()?.let { RpcClientDisconnectedException(null, it) }
                                 ?: cause
            cc.resumeWithException(exToResumeWith)
          }
        }
      }.let { result ->
        logger.trace { "Resumed $requestId, serving response streams" }
        // resumed successfully, start serving streams
        val resource = completedRpc.remove(requestId)?.also {
          for (internalDescriptor in it.streams) {
            serveStream(internalDescriptor, it.prefetchStrategy)
          }
        } ?: run {
          logger.trace { "No resources assigned for $requestId, was it cancelled already?" }
          null
        }
        val disposable = SuspendInvocationHandler.CallResult(result) {
          resource?.let(::disposeResponseResource)
        }
        publish(disposable)
        logger.trace { "Result published for request $requestId" }

      }
    } ?: throw RpcTimeoutException("Request $uninterceptedRequest has timed out after ${RPC_TIMEOUT}ms", cause = null)
  }

  private fun registerStreams(
    list: List<StreamDescriptor>,
    route: UID,
    prefetchStrategy: PrefetchStrategy,
  ): List<InternalStreamDescriptor> {
    return list.map { descriptor -> registerStream(descriptor, route, prefetchStrategy) }
  }

  private fun registerStream(descriptor: StreamDescriptor, route: UID, prefetchStrategy: PrefetchStrategy): InternalStreamDescriptor {
    return InternalStreamDescriptor.fromDescriptor(descriptor, route, prefetchStrategy, coroutineScope).also {
      streams[descriptor.uid] = it
    }
  }

  private fun serveStream(descriptor: InternalStreamDescriptor, prefetchStrategy: PrefetchStrategy) {
    val route = descriptor.route
    serveStream(origin = origin,
                coroutineScope = coroutineScope,
                descriptor = descriptor,
                prefetchStrategy = prefetchStrategy,
                registerStream = { stream -> registerStream(stream, route, prefetchStrategy) },
                unregisterStream = { streamId -> streams.remove(streamId) },
                wrapThrowable = { cause ->
                  cause.causeOfType<TransportDisconnectedException>()?.let { RpcClientDisconnectedException(null, it) } ?: cause
                },
                sendAsync = ::sendAsync)
  }
}
