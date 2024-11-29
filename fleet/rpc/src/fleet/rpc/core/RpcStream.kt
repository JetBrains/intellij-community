// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.rpc.core

import fleet.util.AtomicRef
import fleet.util.UID
import fleet.util.async.coroutineNameAppended
import fleet.util.logging.logger
import io.opentelemetry.context.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.serialization.KSerializer
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resumeWithException
import kotlin.math.max
import kotlin.math.min

data class RpcToken(val token: UID) : CoroutineContext.Element {
  companion object : CoroutineContext.Key<RpcToken>

  override val key: CoroutineContext.Key<*>
    get() = RpcToken
}


private object RpcStream {
  val logger = logger<RpcStream>()
}

sealed class StreamDirection {
  class ToRemote(val channel: ReceiveChannel<Any?>) : StreamDirection()
  class FromRemote(val channel: SendChannel<Any?>) : StreamDirection()
}

class StreamDescriptor(val displayName: String,
                       val uid: UID,
                       val token: RpcToken?,
                       val direction: StreamDirection,
                       val elementSerializer: KSerializer<Any?>) {

  override fun toString(): String {
    return "StreamDescriptor: ${displayName}"
  }
}

class Budget(initial: Int) {
  private val state = AtomicRef(State(null, initial, null))

  private data class State(val cancellation: CancellationException?, val budget: Int, val continuation: CancellableContinuation<Unit>?)

  private fun withdraw(): Boolean {
    return state.getAndUpdate { it.copy(budget = max(0, it.budget - 1)) }.budget > 0
  }

  private suspend fun await() {
    suspendCancellableCoroutine { continuation ->
      val r = state.updateAndGet {
        require(it.continuation == null) { "Budget is not intended to use by several producers" }
        when {
          it.budget > 0 -> it
          it.cancellation == null -> it.copy(continuation = continuation)
          else -> it
        }
      }
      when {
        r.cancellation != null -> continuation.resumeWithException(r.cancellation)
        r.budget > 0 -> continuation.resumeWith(Result.success(Unit))
      }
    }
  }

  internal suspend fun withdrawSuspend() {
    while (!withdraw()) {
      await()
    }
  }

  fun refill(quantity: Int) {
    require(quantity > 0)
    val r = state.getAndUpdate {
      it.copy(budget = it.budget + quantity, continuation = null)
    }
    r.continuation?.resumeWith(Result.success(Unit))
  }

  internal fun cancel(cause: CancellationException) {
    val was = state.getAndUpdate {
      if (it.cancellation == null) {
        it.copy(cancellation = cause, continuation = null)
      }
      else {
        it
      }
    }
    if (was.cancellation == null) {
      was.continuation?.resumeWithException(cause)
    }
  }
}

sealed class InternalStreamMessage {
  data class Payload(val payload: Any?) : InternalStreamMessage()
}

sealed class InternalStreamDescriptor {
  abstract val route: UID
  abstract val token: RpcToken?
  abstract val displayName: String
  abstract val uid: UID
  abstract val elementSerializer: KSerializer<Any?>

  data class ToRemote(override val route: UID,
                      override val displayName: String,
                      override val uid: UID,
                      override val token: RpcToken?,
                      override val elementSerializer: KSerializer<Any?>,
                      val channel: ReceiveChannel<Any?>,
                      val budget: Budget) : InternalStreamDescriptor()

  data class FromRemote(override val route: UID,
                        override val displayName: String,
                        override val uid: UID,
                        override val token: RpcToken?,
                        override val elementSerializer: KSerializer<Any?>,
                        val channel: SendChannel<Any?>,
                        val prefetchStrategy: PrefetchStrategy,
                        val bufferedChannel: Channel<InternalStreamMessage>) : InternalStreamDescriptor()

  companion object {
    fun fromDescriptor(desc: StreamDescriptor, route: UID, prefetchStrategy: PrefetchStrategy): InternalStreamDescriptor {
      return when (desc.direction) {
        is StreamDirection.FromRemote -> FromRemote(route = route,
                                                    displayName = desc.displayName,
                                                    uid = desc.uid,
                                                    elementSerializer = desc.elementSerializer,
                                                    channel = desc.direction.channel,
                                                    bufferedChannel = Channel(Channel.UNLIMITED),
                                                    token = desc.token,
                                                    prefetchStrategy = prefetchStrategy)
        is StreamDirection.ToRemote -> ToRemote(route = route,
                                                displayName = desc.displayName,
                                                uid = desc.uid,
                                                elementSerializer = desc.elementSerializer,
                                                token = desc.token,
                                                channel = desc.direction.channel,
                                                budget = Budget(0))
      }
    }
  }

  private fun requireStreamFromRemote(): FromRemote {
    return when (this) {
      is FromRemote -> this
      else -> error("${this.displayName} is not a channel from remote")
    }
  }

  fun requireStreamToRemote(): ToRemote {
    return when (this) {
      is ToRemote -> this
      else -> error("${this.displayName} is not a channel to remote")
    }
  }

  fun requireBufferedChannel(): Channel<InternalStreamMessage> {
    return requireStreamFromRemote().bufferedChannel
  }
}

interface PrefetchStrategy {
  fun streamStarted(): Int
  fun messageReceived(requested: Int, remaining: Int): Int?

  companion object {

    private val logger = logger<PrefetchStrategy>()

    //@fleet.kernel.plugins.InternalInPluginModules(where = ["fleet.app.fleet.tests"])
    const val STREAM_BURST_SIZE = 100

    val Default = object : PrefetchStrategy {
      override fun streamStarted(): Int = STREAM_BURST_SIZE

      override fun messageReceived(requested: Int, remaining: Int): Int? {
        val out = STREAM_BURST_SIZE.takeIf { remaining < 10 }
        logger.trace { "DefaultPrefetch: requested $requested, remaining $remaining, out $out" }
        return out
      }
    }

    val Exponential = object : PrefetchStrategy {
      override fun streamStarted(): Int = 1

      override fun messageReceived(requested: Int, remaining: Int): Int? = min(requested * 2, STREAM_BURST_SIZE).takeIf { remaining == 0 }
    }
  }
}

fun serveStream(origin: UID,
                coroutineScope: CoroutineScope,
                descriptor: InternalStreamDescriptor,
                prefetchStrategy: PrefetchStrategy,
                registerStream: (StreamDescriptor) -> InternalStreamDescriptor,
                unregisterStream: (UID) -> InternalStreamDescriptor?,
                wrapThrowable: (Throwable) -> Throwable = { it },
                sendAsync: (TransportMessage, RequestCompletionHandler?) -> Unit) {

  RpcStream.logger.trace { "serveStream ${descriptor.uid} ${descriptor.displayName} route=${descriptor.route}" }
  val coroutineName = coroutineScope.coroutineNameAppended(descriptor.displayName)
  fun transportMessage(message: RpcMessage) =
    message.seal(destination = descriptor.route, origin = origin, otelData = Context.current().toTelemetryData())

  suspend fun sendMessage(message: RpcMessage) {
    sendSuspend(sendAsync, transportMessage(message))
  }

  when (descriptor) {
    is InternalStreamDescriptor.ToRemote -> {
      coroutineScope.launch(coroutineName) {
        try {
          sendMessage(RpcMessage.StreamInit(streamId = descriptor.uid))
          for (item in descriptor.channel) {
            val json = rpcJsonImplementationDetail()
            val (jsonElement, streamDescriptors) = withSerializationContext("Sub-channel of ${descriptor.uid}", token = descriptor.token,
                                                                            rpcScope = coroutineScope) {
              json.encodeToJsonElement(serializer = descriptor.elementSerializer,
                                       value = item)
            }
            // register streams before we publish them to remote with `sendAsync` or we may miss some messages from FROM_REMOTE streams if remote is fast enough
            val internalStreamDescriptors = streamDescriptors.map { registerStream(it) }
            descriptor.budget.withdrawSuspend()
            RpcStream.logger.trace { "Sending in stream ${descriptor.uid} <${descriptor.displayName}> item ${item?.javaClass?.simpleName}($item)" }
            sendMessage(RpcMessage.StreamData(streamId = descriptor.uid, data = jsonElement))
            // we must serve TO_REMOTE streams only after initial message was sent
            for (internalStream in internalStreamDescriptors) {
              serveStream(origin = origin,
                          coroutineScope = coroutineScope,
                          descriptor = internalStream,
                            prefetchStrategy = prefetchStrategy,
                          registerStream = registerStream,
                          unregisterStream = unregisterStream,
                          sendAsync = sendAsync)
            }
          }
          sendAsync(transportMessage(RpcMessage.StreamClosed(streamId = descriptor.uid)), null)
        }
        catch (e: Throwable) {
          val wrapped = wrapThrowable(e)
          descriptor.channel.cancel(CancellationException("cancelled with reason", wrapped))

          if (e is CancellationException) {
            sendAsync(transportMessage(RpcMessage.StreamClosed(streamId = descriptor.uid, error = FailureInfo(
              producerCancelled = "stream to remote is cancelled due to ${e.stackTraceToString()}"))), null)
            throw e
          }
          sendAsync(transportMessage(RpcMessage.StreamClosed(streamId = descriptor.uid, error = e.toFailureInfo())), null)
        }
        finally {
          unregisterStream(descriptor.uid)
        }
      }
    }
    is InternalStreamDescriptor.FromRemote -> {
      val userChannel = descriptor.channel

      fun cancelFromRemoteByUserRequest(cause: Throwable?) {
        // descriptor is still there if the channel was canceled by a user
        unregisterStream(descriptor.uid)?.let {
          RpcStream.logger.trace { "Cancelling the stream ${descriptor.uid} ${descriptor.displayName} by user request" }
          // concurrent coroutine tries to offer incoming StreamData to this channel, it will ignore CancellationException
          it.requireBufferedChannel().cancel(CancellationException("Cancelled from user side", cause))
          val failure = when (cause) {
            is CancellationException -> {
              // consumer has cancelled their ReceiveChannel, this is not an error, we should propagate it to remote as StreamClosed without an error
              null
            }
            else -> cause?.toFailureInfo()
          }
          val closedMessage = RpcMessage.StreamClosed(descriptor.uid, failure).seal(destination = descriptor.route,
                                                                                    origin = origin,
                                                                                    otelData = null)
          sendAsync(closedMessage, null)
        }
      }

      coroutineScope.launch(coroutineName) {
        try {
          try {
            userChannel.invokeOnClose {
              RpcStream.logger.trace { "Channel ${descriptor.uid} ${descriptor.displayName} invokeOnClose" }
              cancelFromRemoteByUserRequest(it)
            }
          }
          catch (ex: IllegalStateException) {
            // another handler was already registered
            val cause = RuntimeException(
              "SendChannel ${descriptor.displayName} is used in rpc calls twice, this is not supported. " +
              "If you are using `invokeOnClose` yourself, can you please leave this slot for RpcClient, this is the only way it can send StreamClosed",
              ex)
            userChannel.close(cause)
            cancelFromRemoteByUserRequest(cause)
            return@launch
          }
          try {
            var requested = prefetchStrategy.streamStarted()
            var remaining = requested
            require(requested > 0)
            sendMessage(RpcMessage.StreamNext(descriptor.uid, requested))
            descriptor.bufferedChannel.consumeEach { each ->
              RpcStream.logger.trace { "Channel ${descriptor.uid} ${descriptor.displayName} processes message $each" }
              when (each) {
                is InternalStreamMessage.Payload -> userChannel.send(each.payload)
              }
              remaining -= 1
              prefetchStrategy.messageReceived(requested, remaining)?.let {
                sendMessage(RpcMessage.StreamNext(descriptor.uid, it))
                requested = it
                remaining += it
              }
            }
            userChannel.close()
          }
          catch (ex: Throwable) {
            userChannel.close(wrapThrowable(ex))
            throw ex
          }
        }
        catch (ex: Throwable) {
          RpcStream.logger.trace(ex) { "Channel ${descriptor.uid} ${descriptor.displayName} is closed" }
        }
        finally {
          RpcStream.logger.trace { "No longer serving ${descriptor.uid} ${descriptor.displayName}" }
        }
      }
    }
  }
}
