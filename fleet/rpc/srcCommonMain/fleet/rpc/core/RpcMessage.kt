// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.rpc.core

import fleet.util.UID
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

@Serializable
sealed class RpcMessage {
  companion object {
    private val serializer by lazy { serializer() }
  }

  fun seal(destination: UID, origin: UID): TransportMessage {
    return TransportMessage.Envelope(destination = destination,
                                     origin = origin,
                                     payload = Json.encodeToString(serializer, this))
  }

  @Serializable
  @SerialName("call")
  data class CallRequest(val requestId: UID,
                         val service: InstanceId,
                         val method: String,
                         val args: Map<String, JsonElement>,
                         val meta: Map<String, JsonElement> = emptyMap()) : RpcMessage() {
    val displayName: String get() = "RPC call ${classMethodDisplayName()}[#${requestId}]"

    fun classMethodDisplayName(): String {
      return classMethodDisplayName(service.id, method)
    }

    override fun toString(): String {
      return "CallRequest $requestId: ${classMethodDisplayName()} " + args.map { (k, v) -> "$k => $v" }.joinToString()
    }
  }

  @Serializable
  @SerialName("call_result")
  data class CallResult(val requestId: UID,
                        val result: JsonElement,
                        val meta: Map<String, JsonElement> = emptyMap()) : RpcMessage()

  @Serializable
  @SerialName("call_failure")
  data class CallFailure(val requestId: UID,
                         val error: FailureInfo) : RpcMessage()

  @Serializable
  @SerialName("cancel_call")
  data class CancelCall(val requestId: UID) : RpcMessage()

  @Serializable
  @SerialName("stream_data")
  data class StreamData(val streamId: UID,
                        val data: JsonElement) : RpcMessage()

  // optional message sent by a producer to a newly created stream
  //
  // in some situations stream arrives late when it is not needed anymore
  // consumer should somehow communicate this fact
  // instead of keeping track of all these "lingering" streams (and streams inside of these streams)
  // when consumer receives StreamInit for a stream that is not registered as in-use, it can respond with StreamClose
  @Serializable
  @SerialName("stream_init")
  data class StreamInit(val streamId: UID): RpcMessage()

  /**
   * Producer should not send any data unless asked to. Consumer controls the moment and the quantity with this message.
   * Usually this will be the first message sent by the consumer.
   */
  @Serializable
  @SerialName("stream_next")
  data class StreamNext(val streamId: UID, val count: Int) : RpcMessage()

  @Serializable
  @SerialName("stream_closed")
  data class StreamClosed(val streamId: UID,
                          val error: FailureInfo? = null) : RpcMessage()

  @Serializable
  @SerialName("resource_consumed")
  data class ResourceConsumed(val resourcePath: InstanceId) : RpcMessage()
}