// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:UseSerializers(SendChannelSerializer::class, ReceiveChannelSerializer::class)

package fleet.rpc.core

import fleet.rpc.core.Blob.Companion.serializer
import fleet.util.Base64WithOptionalPadding
import fleet.util.UID
import fleet.util.UIDSerializer
import fleet.util.channels.channels
import fleet.util.serialization.DataSerializer
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.flow.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.reflect.KClass

private class SerializationContext(val streamDescriptors: MutableList<StreamDescriptor>,
                                   val callJob: Job?,
                                   val rpcCoroutineScope: CoroutineScope,
                                   val token: RpcToken?,
                                   val displayName: String)

private val SerializationContextThreadLocal: ThreadLocal<SerializationContext> = ThreadLocal<SerializationContext>()

fun <T> withSerializationContext(displayName: String,
                                 token: RpcToken?,
                                 rpcScope: CoroutineScope,
                                 callJob: Job? = null,
                                 f: () -> T): Pair<T, List<StreamDescriptor>> {
  val old = SerializationContextThreadLocal.get()
  try {
    val ctx = SerializationContext(streamDescriptors = mutableListOf(),
                                   callJob = callJob,
                                   token = token,
                                   rpcCoroutineScope = rpcScope,
                                   displayName = displayName)
    SerializationContextThreadLocal.set(ctx)
    val r = f()
    return r to ctx.streamDescriptors
  }
  finally {
    SerializationContextThreadLocal.set(old)
  }
}

private inline fun <reified T> requireSerializationContext() = checkNotNull(SerializationContextThreadLocal.get()) {
  "Serialization and deserialization of ${T::class} requires SerializationContextThreadLocal to be bound"
}

class SendChannelSerializer<T>(private val elementSerializer: KSerializer<T>) : DataSerializer<SendChannel<T>, UID>(UIDSerializer) {

  @Suppress("UNCHECKED_CAST")
  override fun fromData(data: UID): SendChannel<T> {
    val ctx = requireSerializationContext<SendChannel<T>>()
    val (sender, receiver) = channels<T>()
    val stream = StreamDescriptor(displayName = ctx.displayName,
                                  uid = data,
                                  token = ctx.token,
                                  elementSerializer = elementSerializer as KSerializer<Any?>,
                                  direction = StreamDirection.ToRemote(receiver))
    ctx.streamDescriptors.add(stream)
    return sender
  }

  @Suppress("UNCHECKED_CAST")
  override fun toData(value: SendChannel<T>): UID {
    val ctx = requireSerializationContext<SendChannel<T>>()
    val uid = UID.random()
    val stream = StreamDescriptor(displayName = ctx.displayName,
                                  uid = uid,
                                  elementSerializer = elementSerializer as KSerializer<Any?>,
                                  token = ctx.token,
                                  direction = StreamDirection.FromRemote(value as SendChannel<Any?>))
    ctx.streamDescriptors.add(stream)
    return uid
  }
}

class ReceiveChannelSerializer<T>(private val elementSerializer: KSerializer<T>) : DataSerializer<ReceiveChannel<T>, UID>(UIDSerializer) {
  @Suppress("UNCHECKED_CAST")
  override fun fromData(data: UID): ReceiveChannel<T> {
    val ctx = requireSerializationContext<ReceiveChannel<T>>()
    val (sender, receiver) = channels<T>(Channel.BUFFERED)
    val stream = StreamDescriptor(displayName = ctx.displayName,
                                  uid = data,
                                  elementSerializer = elementSerializer as KSerializer<Any?>,
                                  token = ctx.token,
                                  direction = StreamDirection.FromRemote(sender as SendChannel<Any?>))
    ctx.streamDescriptors.add(stream)
    return receiver
  }

  @Suppress("UNCHECKED_CAST")
  override fun toData(value: ReceiveChannel<T>): UID {
    val ctx = requireSerializationContext<ReceiveChannel<T>>()
    val uid = UID.random()
    val stream = StreamDescriptor(displayName = ctx.displayName,
                                  uid = uid,
                                  elementSerializer = elementSerializer as KSerializer<Any?>,
                                  token = ctx.token,
                                  direction = StreamDirection.ToRemote(value))
    ctx.streamDescriptors.add(stream)
    return uid
  }
}

//@fleet.kernel.plugins.InternalInPluginModules(where = ["fleet.app.fleet.tests"])
@Deprecated("please don't use directly, use RpcFlow instead")
class FlowSerializer<T>(elementSerializer: KSerializer<T>) :
  DataSerializer<Flow<T>, ReceiveChannel<T>>(ReceiveChannelSerializer(elementSerializer)) {

  override fun fromData(data: ReceiveChannel<T>): Flow<T> {
    return data.consumeAsFlow()
  }

  override fun toData(value: Flow<T>): ReceiveChannel<T> {
    val ctx = requireSerializationContext<Flow<T>>()
    return value.produceIn(ctx.rpcCoroutineScope)
  }
}

class DeferredSerializer<T>(elementSerializer: KSerializer<T>) :
  DataSerializer<Deferred<T>, ReceiveChannel<T>>(ReceiveChannelSerializer(elementSerializer)) {

  override fun fromData(data: ReceiveChannel<T>): Deferred<T> {
    val ctx = requireSerializationContext<Deferred<T>>()

    class Box(val v: T)

    val d = CompletableDeferred<T>()
    d.invokeOnCompletion { cause ->
      data.cancel(cause?.let { CancellationException("cancelled with reason", it) })
    }
    ctx.rpcCoroutineScope.launch {
      try {
        val v = data.consumeAsFlow().map { v -> Box(v) }.firstOrNull()
        if (v != null) {
          d.complete(v.v)
        }
        else {
          d.cancel()
        }
      }
      catch (ex: CancellationException) {
        d.cancel(ex)
      }
      catch (ex: Throwable) {
        d.completeExceptionally(ex)
      }
    }
    return d
  }

  override fun toData(value: Deferred<T>): ReceiveChannel<T> {
    val ctx = requireSerializationContext<Deferred<T>>()
    val (sender, receiver) = channels<T>()
    sender.invokeOnClose { cause ->
      value.cancel(cause?.let { CancellationException("cancelled with reason", it) })
    }
    ctx.rpcCoroutineScope.launch {
      try {
        val v = value.await()
        sender.send(v)
        sender.close()
      }
      catch (ex: CancellationException) {
        sender.close()
      }
      catch (ex: Throwable) {
        sender.close(ex)
      }
    }
    return receiver
  }
}

@Serializable
internal data class Scope(val cancellation: ReceiveChannel<Unit>, val completion: SendChannel<Unit>)

internal object CoroutineScopeSerializer : DataSerializer<CoroutineScope, Scope>(Scope.serializer()) {
  override fun fromData(data: Scope): CoroutineScope {
    val ctx = requireSerializationContext<CoroutineScope>()
    val supervisorJob = SupervisorJob(ctx.rpcCoroutineScope.coroutineContext[Job])
    val scopeJob = Job(supervisorJob)

    val callJob = requireNotNull(ctx.callJob) { "CoroutineScopes can be deserialized only as args of call method, not stream data" }
    ctx.rpcCoroutineScope.launch {
      try {
        callJob.join()
        // This completes only the created CompletableJob; we'll wait for children in `invokeOnCompletion`
        scopeJob.complete()
      }
      catch (t: Throwable) {
        scopeJob.completeExceptionally(t)
      }
    }

    // This waits for both scopeJob and its children completion
    scopeJob.invokeOnCompletion { cause ->
      data.completion.close(cause)
    }
    ctx.rpcCoroutineScope.launch { data.cancellation.consumeEach { } }.invokeOnCompletion { scopeJob.cancel() }
    return CoroutineScope(ctx.rpcCoroutineScope.coroutineContext + CoroutineExceptionHandler { _, _ -> } + scopeJob)
  }

  override fun toData(value: CoroutineScope): Scope {
    val (completion_sender, completion_receiver) = channels<Unit>()
    val (cancellation_sender, cancellation_receiver) = channels<Unit>()
    value.launch {
      try {
        completion_receiver.consumeEach { }
      }
      finally {
        cancellation_sender.close()
      }
    }
    return Scope(cancellation_receiver, completion_sender)
  }
}

fun KClass<*>.fasterIsSubclassOf(c: KClass<*>): Boolean =
  c.java.isAssignableFrom(this.java)

private fun <T : Any> KSerializer<T>.nullable(shouldBeNullable: Boolean): KSerializer<T?> =
  @Suppress("UNCHECKED_CAST")
  when {
    shouldBeNullable -> nullable
    else -> this as KSerializer<T?>
  }

private val RpcJson: Json by lazy {
  Json {
    this.classDiscriminator = "type"
    this.allowStructuredMapKeys = true
    this.ignoreUnknownKeys = false
    this.encodeDefaults = true
  }
}

fun rpcJsonImplementationDetail(): Json = 
  RpcJson

/**
 * when resolving ktype to kserializer kotlinx.serialization will unconditionally use built in serializers before contextual ones
 * this means serializers with type parameters like SendChannelSerializer will always receive default ByteArraySerializer for SendChannel<ByteArray>
 *
 * this can be worked around by explicit check for ByteArray in our wrappers like [serializer] but will not help generic classes like Socket
 */
@Serializable(with = BlobSerializer::class)
class Blob(val bytes: ByteArray) {
  override fun equals(other: Any?): Boolean {
    return this === other || (other is Blob
                              && bytes.contentEquals(other.bytes))
  }

  override fun hashCode(): Int {
    return bytes.contentHashCode()
  }

  override fun toString(): String {
    return "Blob(size=${bytes.size}, hash=${hashCode().toString(16)}"
  }
}

//@fleet.kernel.plugins.InternalInPluginModules(where = ["fleet.common", "fleet.protocol"])
object BlobSerializer : DataSerializer<Blob, String>(String.serializer()) {
  @OptIn(ExperimentalEncodingApi::class)
  override fun fromData(data: String): Blob {
    return Blob(Base64WithOptionalPadding.decode(data))
  }

  @OptIn(ExperimentalEncodingApi::class)
  override fun toData(value: Blob): String {
    return Base64WithOptionalPadding.encode(value.bytes)
  }
}

class ThrowingSerializer<T>(private val debugInfo: String) : DataSerializer<T, String>(String.serializer()) {
  override fun fromData(data: String): T {
    error(debugInfo)
  }

  override fun toData(value: T): String {
    error(debugInfo)
  }
}
