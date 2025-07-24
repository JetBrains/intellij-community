// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.rpc.server

import fleet.rpc.RemoteApi
import fleet.rpc.RemoteApiDescriptor
import fleet.rpc.RemoteKind
import fleet.rpc.core.*
import fleet.rpc.serializer
import fleet.util.UID
import fleet.util.async.Resource
import fleet.util.async.coroutineNameAppended
import fleet.util.async.useOn
import fleet.util.channels.isFull
import fleet.util.logging.KLoggers
import kotlinx.collections.immutable.toPersistentSet
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.serialization.json.Json
import fleet.multiplatform.shims.ConcurrentHashMap
import fleet.multiplatform.shims.ConcurrentHashSet
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.coroutineContext

class RpcExecutor private constructor(
  private val services: RpcServiceLocator,
  private val route: UID,
  private val queue: SendChannel<Pair<TransportMessage, ((Throwable?) -> Unit)?>>,
  private val fallbackCoroutineScope: CoroutineScope,
  private val rpcInterceptor: RpcExecutorMiddleware,
  private val rpcCallDispatcher: CoroutineDispatcher?,
) {

  private val remoteObjects = ConcurrentHashMap<InstanceId, ServiceImplementation>()
  private val resources = ConcurrentHashMap<InstanceId, Job>()
  private val children: ConcurrentHashMap<InstanceId, Set<InstanceId>> = ConcurrentHashMap()
  private val parents: ConcurrentHashMap<InstanceId, InstanceId> = ConcurrentHashMap()

  companion object {
    internal val logger = KLoggers.logger(RpcExecutor::class)

    suspend fun serve(
      services: RpcServiceLocator,
      route: UID,
      sendChannel: SendChannel<TransportMessage>,
      receiveChannel: ReceiveChannel<TransportMessage>,
      rpcInterceptor: RpcExecutorMiddleware,
      rpcCallDispatcher: CoroutineDispatcher? = null,
    ) {
      val queueChannel = Channel<Pair<TransportMessage, ((Throwable?) -> Unit)?>>(Channel.UNLIMITED)
      val rpcScope = CoroutineScope(coroutineContext + SupervisorJob(coroutineContext[Job]))
      val executor = RpcExecutor(services = services,
                                 queue = queueChannel,
                                 fallbackCoroutineScope = rpcScope,
                                 rpcInterceptor = rpcInterceptor,
                                 rpcCallDispatcher = rpcCallDispatcher,
                                 route = route)
      coroutineScope {
        launch {
          receiveChannel.consumeEach { message ->
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
        val impl = proxyDesc(message.service) ?: run {
          logger.trace { "Failed to find rpc method for $message" }
          send(RpcMessage.CallFailure(message.requestId,
                                      FailureInfo(unresolvedService = "API for ${message.classMethodDisplayName()} could not be found"))
                 .seal(destination = clientId, origin = route))
          return
        }
        val serviceScope = impl.serviceScope ?: fallbackCoroutineScope
        val requestJob = Job(serviceScope.coroutineContext[Job])
        val signature = impl.remoteApiDescriptor.getSignature(message.method)
        val args = try {
          signature.parameters.map { p ->
            val parameterName = p.parameterName
            val arg = message.args[parameterName]
            requireNotNull(arg) { "missing parameter $parameterName in ${message.service}/${message.method}" }
            val displayName = methodParamDisplayName(message.classMethodDisplayName(), parameterName)
            val kser = p.parameterKind.serializer(message.classMethodDisplayName())
            val (`object`, streamDescriptors) = withSerializationContext(displayName, null, serviceScope) {
              json.decodeFromJsonElement(kser, arg)
            }
            streamDescriptors.forEach {
              registerStream(serviceScope, it, clientId)
              serveStream(serviceScope, requireNotNull(channels[it.uid]), clientId)
            }
            `object`
          }
        }
        catch (ex: Throwable) {
          logger.trace(ex) { "Failed to build arguments for $message" }
          send(RpcMessage.CallFailure(message.requestId,
                                      FailureInfo(requestError = "Invalid arguments for ${message.classMethodDisplayName()}: ${ex}"))
                 .seal(destination = clientId, origin = route))
          return
        }

        registerRequest(message.requestId, requestJob, clientId)
        val requestContext = requestJob +
                             serviceScope.coroutineContext.coroutineNameAppended(message.displayName) +
                             (rpcCallDispatcher ?: EmptyCoroutineContext)
        serviceScope.launch(requestContext) {
          try {
            val registeredStreams = mutableListOf<InternalStreamDescriptor>()
            logger.trace { "Executing interceptor for request  ${message.requestId}" }
            val result = rpcInterceptor.execute(message) { request ->
              logger.trace { "Executing method for request  ${message.requestId}" }
              val result = (impl.remoteApiDescriptor as RemoteApiDescriptor<RemoteApi<*>>).call(impl.instance, message.method, args.toTypedArray())
              logger.trace { "Got result for request  ${message.requestId}" }
              val remoteObjectId = InstanceId(UID.random().toString())
              val returnType = signature.returnType

              if (result is RemoteObject) {
                registerRemoteObject(
                  path = remoteObjectId,
                  remoteApiDescriptor = (returnType as RemoteKind.RemoteObject).descriptor,
                  inst = result,
                  serviceScope = serviceScope,
                  parent = message.service
                )
              }

              if (impl.instance is RemoteObject && request.method == "clientDispose") {
                unregisterRemoteObject(message.service)
              }

              val resultSerialized = if (result is RemoteObject) {
                Json.encodeToJsonElement(InstanceId.serializer(), remoteObjectId)
              }
              else if (returnType is RemoteKind.Resource) {
                val ready = CompletableDeferred<RemoteResource>()

                val job = serviceScope.launch {
                  val resource = (result as Resource<RemoteResource>).useOn(this).await()
                  ready.complete(resource)
                }

                job.invokeOnCompletion { throwable ->
                  if (throwable != null) ready.completeExceptionally(throwable)
                }

                registerResource(
                  remoteObjectId, returnType.descriptor, ready.await(), job,
                  parent = message.service,
                  serviceScope = serviceScope,
                )

                Json.encodeToJsonElement(InstanceId.serializer(), remoteObjectId)
              }
              else {
                val (resultSerialized, streamDescriptors) = withSerializationContext("Result of ${message.requestId}", null, serviceScope) {
                  val kserializer = returnType.serializer(message.classMethodDisplayName())
                  json.encodeToJsonElement(kserializer, result)
                }

                streamDescriptors.forEach {
                  registeredStreams.add(registerStream(serviceScope, it, clientId))
                }

                resultSerialized
              }

              logger.trace { "Sending result: requestId=${request.requestId}, result=$result" }
              RpcMessage.CallResult(requestId = request.requestId,
                                    result = resultSerialized)
            }
            sendAsync(result.seal(destination = clientId, origin = route)) { ex ->
              if (ex == null) {
                registeredStreams.forEach {
                  serveStream(serviceScope, it, clientId)
                }
              }
            }
          }
          catch (e: Throwable) {
            logger.trace { "Sending call failure: requestId=${message.requestId}, error=${e.message}" }
            send(RpcMessage.CallFailure(requestId = message.requestId,
                                        error = e.toFailureInfo())
                   .seal(destination = clientId, origin = route))
            // todo removeREquest ... completeExceptionally()
          }

          removeRequest(message.requestId, clientId) { complete() }
        }
      }

      is RpcMessage.CancelCall -> {
        logger.trace { "Cancelling call: requestId=${message.requestId}" }
        //TODO probably should cancel all streams as well, we can't know if they are handled by someone
        removeRequest(message.requestId, clientId) { cancel("Cancelled by Message.Cancel") }
      }

      is RpcMessage.StreamData -> {
        val stream = channels[message.streamId]
        if (stream != null) {
          val (de, streamDescriptors) = withSerializationContext("sub-channel of ${message.streamId}", null, stream.serviceScope) {
            json.decodeFromJsonElement(stream.elementSerializer, message.data)
          }

          streamDescriptors.forEach {
            serveStream(stream.serviceScope, registerStream(stream.serviceScope, it, clientId), clientId)
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
          sendAsync(RpcMessage.StreamClosed(message.streamId).seal(clientId, route))
        }
      }
      is RpcMessage.ResourceConsumed -> {
        unregisterResource(message.resourcePath)
      }
      else -> error("Unexpected message $message")
    }
  }

  private fun cancelAllOngoingWork(clientId: UID) {
    routeRequests[clientId]?.onEach { requestId ->
      removeRequest(requestId, clientId) {
        cancel("Cancelled by Message.RouteClosed")
      }
    }
    routeChannels[clientId]?.onEach { channelId -> closeChannel(channelId, clientId) }
  }

  private fun serveStream(coroutineScope: CoroutineScope, descriptor: InternalStreamDescriptor, clientId: UID) {
    serveStream(origin = route,
                coroutineScope = coroutineScope,
                descriptor = descriptor,
                registerStream = { stream -> registerStream(coroutineScope, stream, clientId) },
                unregisterStream = { streamId ->
                  channels.remove(streamId)?.also { s -> routeChannels[s.route]?.remove(s.uid) }
                },
                sendAsync = ::sendAsync,
                prefetchStrategy = PrefetchStrategy.Default)
  }

  private fun registerStream(
    serviceScope: CoroutineScope,
    descriptor: StreamDescriptor,
    route: UID,
  ): InternalStreamDescriptor {
    val registeredStream = InternalStreamDescriptor.fromDescriptor(
      desc = descriptor,
      route = route,
      prefetchStrategy = PrefetchStrategy.Default,
      scope = serviceScope,
    )
    val previous = channels.put(descriptor.uid, registeredStream)
    require(previous == null) {
      "There is no way you can use the same channel twice ${descriptor.displayName}"
    }
    routeChannels.computeIfAbsent(route) { ConcurrentHashSet() }.add(descriptor.uid)
    return registeredStream
  }

  private fun registerRequest(
    requestId: UID,
    requestJob: CompletableJob,
    route: UID?,
  ) {
    requestJobs[requestId] = requestJob
    if (route != null) routeRequests.computeIfAbsent(route) { ConcurrentHashSet() }.add(requestId)
  }

  private fun removeRequest(requestId: UID, route: UID?, jobAction: CompletableJob.() -> Unit) {
    requestJobs.remove(requestId)?.jobAction()
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

  private fun registerRemoteObject(
    path: InstanceId,
    remoteApiDescriptor: RemoteApiDescriptor<*>,
    inst: RemoteApi<*>,
    parent: InstanceId,
    serviceScope: CoroutineScope,
  ) {
    val impl = ServiceImplementation(remoteApiDescriptor, inst, serviceScope)
    remoteObjects.putIfAbsent(path, impl)?.let { old ->
      if (old.instance !== inst) {
        error(
          "Path must be unique. Previously registered object: '${old.instance}' has same path '$path' as currently being registered '$inst'")
      }
    }

    // Add dependency to the created remote object
    children.compute(parent) { k, v -> v.orEmpty().toPersistentSet().add(path) }
    parents[path] = parent
  }

  private fun registerResource(
    path: InstanceId,
    remoteApiDescriptor: RemoteApiDescriptor<*>,
    inst: RemoteApi<*>,
    job: Job,
    parent: InstanceId,
    serviceScope: CoroutineScope,
  ) {
    registerRemoteObject(path, remoteApiDescriptor, inst, parent, serviceScope)
    resources.putIfAbsent(path, job)?.let { error("cannot register two resource with the same path") }
  }

  private fun unregisterRemoteObject(path: InstanceId, additionalStep: ((InstanceId) -> Unit)? = null) {
    remoteObjects.remove(path)
    additionalStep?.invoke(path)

    // Remove from parent deps, and unregister children
    parents.remove(path)?.let { parent -> children.computeIfPresent(parent) { _, deps -> deps.toPersistentSet().remove(path) } }
    children.remove(path)?.forEach {
      unregisterRemoteObject(it, additionalStep)
    }
  }

  private fun unregisterResource(path: InstanceId) {
    unregisterRemoteObject(path) {
      resources.remove(path)?.cancel()
    }
  }

  private fun proxyDesc(serviceId: InstanceId): ServiceImplementation? {
    return remoteObjects[serviceId] ?: services.resolve(serviceId)
  }
}