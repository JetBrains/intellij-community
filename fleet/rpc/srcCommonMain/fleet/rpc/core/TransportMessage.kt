// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.rpc.core

import fleet.util.UID
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
sealed class TransportMessage {

  // RequestDispatcher will broadcast this to all active connections when new connection is established
  @Serializable
  @SerialName("opened")
  data class RouteOpened(val address: UID) : TransportMessage()

  // RequestDispatcher will broadcast this to all active connections when any connection is closed
  @Serializable
  @SerialName("closed")
  data class RouteClosed(val address: UID) : TransportMessage()

  @Serializable
  @SerialName("envelope")
  data class Envelope(val destination: UID, val origin: UID, val otelData: String? = null, val payload: String) : TransportMessage()

  fun serialize(): String {
    return Json.encodeToString(serializer, this)
  }

  companion object {
    private val serializer by lazy { serializer() }

    fun deserialize(string: String): TransportMessage {
      return Json.decodeFromString(serializer, string)
    }
  }
}

private val rpcMessageSerializer = RpcMessage.serializer()

fun TransportMessage.Envelope.parseMessage(): RpcMessage = Json.decodeFromString(rpcMessageSerializer, payload)
