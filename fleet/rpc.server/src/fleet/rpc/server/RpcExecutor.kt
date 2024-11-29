// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.rpc.server

import fleet.rpc.RemoteApi
import fleet.rpc.RemoteApiDescriptor
import fleet.rpc.RemoteKind
import fleet.rpc.core.*
import fleet.rpc.serializer
import fleet.tracing.TracingCoroutineElement
import fleet.tracing.asContextElement
import fleet.tracing.opentelemetry
import fleet.tracing.tracer
import fleet.util.UID
import fleet.util.async.coroutineNameAppended
import fleet.util.channels.isFull
import fleet.util.logging.KLoggers
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.context.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.coroutineContext

class RpcExecutor private constructor(private val services: RpcServiceLocator,
                                      private val route: UID,
                                      private val queue: SendChannel<Pair<TransportMessage, ((Throwable?) -> Unit)?>>,
                                      private val coroutineScope: CoroutineScope,
                                      private val rpcInterceptor: RpcExecutorMiddleware,
                                      private val rpcCallDispatcher: CoroutineDispatcher?) {

  private val remoteObjects = ConcurrentHashMap<InstanceId, ServiceImplementation>()

  companion object {
    internal val logger = KLoggers.logger(RpcExecutor::class)

    suspend fun serve(services: RpcServiceLocator,
                      route: UID,
                      sendChannel: SendChannel<TransportMessage>,
                      receiveChannel: ReceiveChannel<TransportMessage>,
                      rpcInterceptor: RpcExecutorMiddleware,
                      rpcCallDispatcher: CoroutineDispatcher? = null) {
      val queueChannel = Channel<Pair<TransportMessage, ((Throwable?) -> Unit)?>>(Channel.UNLIMITED)
      val rpcScope = CoroutineScope(coroutineContext + SupervisorJob(coroutineContext[Job]))
      val executor = RpcExecutor(services = services,
                                 queue = queueChannel,
                                 coroutineScope = rpcScope,
                                 rpcInterceptor = rpcInterceptor,
                                 rpcCallDispatcher = rpcCallDispatcher,
                                 route = route)
      coroutineScope {
        launch {
          receiveChannel.consumeEach { message ->
            val otelContext = (message as? TransportMessage.Envelope)?.otelData()?.let { telemetryData ->
              opentelemetry.propagators.textMapPropagator.extract(Context.current(), telemetryData, TelemetryData.otelGetter)
            } ?: Context.current()

            withContext(TracingCoroutineElement(otelContext)) {
              logger.trace { "Received $message" }
              when (message) {
                is TransportMessage.Envelope -> {
                  executor.processRpcMessage(message.origin, message.parseMessage())
                }
                is TransportMessage.RouteClosed -> {
                  executor.cancelAllOngoingWork(message.address)
                }
                is TransportMessage.RouteOpened -> {
                }
              }
            }
          }
        }.invokeOnCompletion {
          logger.trace { "RpcExecutor receiver coroutine has finished" }
          queueChannel.close(it)
          rpcScope.cancel()
        }
        launch {
          queueChannel.consumeEach { (message, completion) ->
            try {
              sendChannel.send(message)
              completion?.invoke(null)
            }
            catch (ex: Throwable) {
              completion?.invoke(ex)
              throw ex
            }
          }
        }.invokeOnCompletion {
          logger.trace { "RpcExecutor sender coroutine has finished" }
          rpcScope.cancel()
        }
      }
    }
  }

  private val requestJobs = ConcurrentHashMap<UID, CompletableJob>()
  private val routeRequests = ConcurrentHashMap<UID/*route*/, MutableSet<UID/*requestId*/>>()
  private val channels = ConcurrentHashMap<UID, InternalStreamDescriptor>()
  private val routeChannels = ConcurrentHashMap<UID/*route*/, MutableSet<UID/*channelId*/>>()
  private val spans = ConcurrentHashMap<UID/*reqId*/, Span>()

  private suspend fun send(message: TransportMessage) {
    sendSuspend(::sendAsync, message)
  }

  private fun sendAsync(envelope: TransportMessage, completion: ((Throwable?) -> Unit)? = null) {
    logger.trace { "Sending $envelope" }
    val sendResult = queue.trySend(envelope to completion)
    require(!sendResult.isFull) { "queue should be unlimited: $sendResult" }
  }

  private suspend fun processRpcMessage(clientId: UID, message: RpcMessage) {
    val json = rpcJsonImplementationDetail()

    when (message) {
      is RpcMessage.CallRequest -> {
        val (remoteApiDescriptor, service) = proxyDesc(message.service) ?: run {
          logger.trace { "Failed to find rpc method for $message" }
          send(RpcMessage.CallFailure(message.requestId,
                                      FailureInfo(unresolvedService = "API for ${message.classMethodDisplayName()} could not be found"))
                 .seal(destination = clientId, origin = route, otelData = null))
          return
        }

        val requestJob = Job(coroutineScope.coroutineContext[Job])
        val signature = remoteApiDescriptor.getSignature(message.method)
        val args = try {
          signature.parameters.map { p ->
            val parameterName = p.parameterName
            val arg = message.args[parameterName]
            requireNotNull(arg) { "missing parameter $parameterName in ${message.service}/${message.method}" }
            val displayName = methodParamDisplayName(message.classMethodDisplayName(), parameterName)
            val kser = p.parameterKind.serializer(message.classMethodDisplayName())
            val (`object`, streamDescriptors) = withSerializationContext(displayName, null, coroutineScope, requestJob) {
              json.decodeFromJsonElement(kser, arg)
            }
            streamDescriptors.forEach {
              registerStream(it, clientId)
              serveStream(requireNotNull(channels[it.uid]), clientId)
            }
            `object`
          }
        }
        catch (ex: Throwable) {
          logger.trace(ex) { "Failed to build arguments for $message" }
          send(RpcMessage.CallFailure(message.requestId,
                                      FailureInfo(requestError = "Invalid arguments for ${message.classMethodDisplayName()}: ${ex}"))
                 .seal(destination = clientId, origin = route, otelData = null))
          return
        }

        val span = tracer.spanBuilder("RPC Call")
          .setAttribute("service", message.service.id)
          .setAttribute("method", message.method)
          .setSpanKind(SpanKind.SERVER)
          .startSpan()
        registerRequest(message.requestId, span, requestJob, clientId)
        val requestContext = requestJob +
                             span.asContextElement() +
                             coroutineScope.coroutineContext.coroutineNameAppended(message.displayName) +
                             (rpcCallDispatcher ?: EmptyCoroutineContext)
        coroutineScope.launch(requestContext) {
          try {
            val registeredStreams = mutableListOf<InternalStreamDescriptor>()
            logger.trace { "Executing interceptor for request  ${message.requestId}" }
            val result = rpcInterceptor.execute(message) { request ->
              logger.trace { "Executing method for request  ${message.requestId}" }
              val result = (remoteApiDescriptor as RemoteApiDescriptor<RemoteApi<*>>).call(service, message.method, args.toTypedArray())
              logger.trace { "Got result for request  ${message.requestId}" }
              val remoteObjectId = InstanceId(UID.random().toString())
              if (result is RemoteObject) {
                registerRemoteObject(remoteObjectId, (signature.returnType as RemoteKind.RemoteObject).descriptor, result)
              }

              if (service is RemoteObject && request.method == "clientDispose") {
                unregisterRemoteObject(message.service)
              }

              val (resultSerialized, streamDescriptors) = withSerializationContext("Result of ${message.requestId}", null, coroutineScope) {
                if (result is RemoteObject) {
                  Json.encodeToJsonElement(InstanceId.serializer(), remoteObjectId)
                }
                else {
                  val kserializer = signature.returnType.serializer(message.classMethodDisplayName())
                  json.encodeToJsonElement(kserializer, result)
                }
              }

              streamDescriptors.forEach {
                registeredStreams.add(registerStream(it, clientId))
              }

              logger.trace { "Sending result: requestId=${request.requestId}, result=$result" }
              RpcMessage.CallResult(requestId = request.requestId,
                                    result = resultSerialized)
            }
            sendAsync(result.seal(destination = clientId, origin = route, otelData = null)) { ex ->
              if (ex == null) {
                registeredStreams.forEach {
                  serveStream(it, clientId)
                }
              }
            }
          }
          catch (e: Throwable) {
            logger.trace { "Sending call failure: requestId=${message.requestId}, error=${e.message}" }
            send(RpcMessage.CallFailure(requestId = message.requestId,
                                        error = e.toFailureInfo())
                   .seal(destination = clientId, origin = route, otelData = null))
            spans[message.requestId]?.setStatus(StatusCode.ERROR, e.message)?.recordException(e)
            // todo removeREquest ... completeExceptionally()
          }

          removeRequest(message.requestId, clientId) { complete() }
        }
      }

      is RpcMessage.CancelCall -> {
        logger.trace { "Cancelling call: requestId=${message.requestId}" }
        //TODO probably should cancel all streams as well, we can't know if they are handled by someone
        removeRequest(message.requestId, clientId, { addEvent("cancel") }) { cancel("Cancelled by Message.Cancel") }
      }

      is RpcMessage.StreamData -> {
        val stream = channels[message.streamId]
        if (stream != null) {
          val (de, streamDescriptors) = withSerializationContext("sub-channel of ${message.streamId}", null, coroutineScope) {
            json.decodeFromJsonElement(stream.elementSerializer, message.data)
          }

          streamDescriptors.forEach { stream ->
            serveStream(registerStream(stream, clientId), clientId)
          }
          runCatching { stream.requireBufferedChannel().send(InternalStreamMessage.Payload(de)) }
            .onFailure { ex ->
              logger.trace(ex) { "Sending to ${message.streamId} failed" }
            }
        }
        else {
          logger.debug { "Stream ${message.streamId} is not registered" }
        }
      }

      is RpcMessage.StreamNext -> {
        channels[message.streamId]?.requireStreamToRemote()?.budget?.refill(message.count)
      }

      is RpcMessage.StreamClosed -> {
        if (!closeChannel(message.streamId, clientId, message.error)) {
          logger.debug { "Stream ${message.streamId} is not registered" }
        }
      }
      is RpcMessage.StreamInit -> {
        if (channels[message.streamId] == null) {
          logger.trace("received StreamInit for unregistered stream ${message.streamId}, will respond with StreamClosed")
          sendAsync(RpcMessage.StreamClosed(message.streamId).seal(clientId, route, null))
        }
      }
      else -> error("Unexpected message $message")
    }
  }

  private fun cancelAllOngoingWork(clientId: UID) {
    routeRequests[clientId]?.onEach { requestId ->
      removeRequest(requestId, clientId, { addEvent("routeClosed") }) {
        cancel("Cancelled by Message.RouteClosed")
      }
    }
    routeChannels[clientId]?.onEach { channelId -> closeChannel(channelId, clientId) }
  }

  private fun serveStream(descriptor: InternalStreamDescriptor, clientId: UID) {
    serveStream(origin = route,
                coroutineScope = coroutineScope,
                descriptor = descriptor,
                registerStream = { stream -> registerStream(stream, clientId) },
                unregisterStream = { streamId ->
                  channels.remove(streamId)?.also { s -> routeChannels[s.route]?.remove(s.uid) }
                },
                sendAsync = ::sendAsync,
                prefetchStrategy = PrefetchStrategy.Default)
  }

  private fun registerStream(descriptor: StreamDescriptor, route: UID): InternalStreamDescriptor {
    val registeredStream = InternalStreamDescriptor.fromDescriptor(descriptor, route, PrefetchStrategy.Default)
    val previous = channels.put(descriptor.uid, registeredStream)
    require(previous == null) {
      "There is no way you can use the same channel twice ${descriptor.displayName}"
    }
    routeChannels.computeIfAbsent(route) { ConcurrentHashMap.newKeySet() }.add(descriptor.uid)
    return registeredStream
  }

  private fun registerRequest(requestId: UID,
                              span: Span,
                              requestJob: CompletableJob,
                              route: UID?) {
    requestJobs[requestId] = requestJob
    spans[requestId] = span
    if (route != null) routeRequests.computeIfAbsent(route) { ConcurrentHashMap.newKeySet() }.add(requestId)
  }

  private fun removeRequest(requestId: UID, route: UID?, spanAction: Span.() -> Unit = {}, jobAction: CompletableJob.() -> Unit) {
    requestJobs.remove(requestId)?.jobAction()
    spans.remove(requestId)?.let { span ->
      span.spanAction()
      span.end()
    }
    if (route != null) routeRequests[route]?.remove(requestId)
  }

  private fun closeChannel(channelId: UID, route: UID?, error: FailureInfo? = null): Boolean {
    val stream = channels.remove(channelId) ?: return false
    if (route != null) routeChannels[route]?.remove(channelId)
    val cause = error?.let { RpcException("Client channel ${stream.displayName} was closed with exception", error) }
    logger.trace { "Closing stream ${stream.displayName} with cause $cause" }
    when (stream) {
      is InternalStreamDescriptor.FromRemote -> {
        stream.bufferedChannel.close(cause)
      }
      is InternalStreamDescriptor.ToRemote -> {
        stream.channel.cancel(CancellationException("Stream closed by remote", cause))
      }
    }
    return true
  }

  private fun registerRemoteObject(path: InstanceId, remoteApiDescriptor: RemoteApiDescriptor<*>, inst: RemoteApi<*>) {
    val impl = ServiceImplementation(remoteApiDescriptor, inst)
    remoteObjects.putIfAbsent(path, impl)?.let { old ->
      if (old.instance !== inst) {
        error(
          "Path must be unique. Previously registered object: '${old.instance}' has same path '$path' as currently being registered '$inst'")
      }
    }
  }

  private fun unregisterRemoteObject(path: InstanceId) {
    remoteObjects.remove(path)
  }

  private fun proxyDesc(serviceId: InstanceId): ServiceImplementation? {
    return remoteObjects[serviceId] ?: services.resolve(serviceId)
  }
}
