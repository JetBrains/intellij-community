// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.rpc.core

import fleet.rpc.core.util.map
import fleet.util.async.Resource
import fleet.util.async.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.Json

interface SocketStats {
  fun outgoingBeforeCompression(size: Int)
  fun outgoingAfterCompression(size: Int)
  fun incomingBeforeCompression(size: Int)
  fun incomingAfterCompression(size: Int)

  companion object {
    fun reportToFlow(state: MutableStateFlow<TransportStats>): SocketStats {
      return object : SocketStats {
        override fun outgoingBeforeCompression(size: Int) {
          state.update { it.copy(sentBytes = it.sentBytes + size) }
        }

        override fun outgoingAfterCompression(size: Int) {
          state.update { it.copy(sentCompressedBytes = it.sentCompressedBytes + size) }
        }

        override fun incomingBeforeCompression(size: Int) {
          state.update { it.copy(receivedCompressedBytes = it.receivedCompressedBytes + size) }
        }

        override fun incomingAfterCompression(size: Int) {
          state.update { it.copy(receivedBytes = it.receivedBytes + size) }
        }
      }
    }
  }
}

sealed class WebSocketMessage {
  class Text(val payload: String) : WebSocketMessage()
  class Binary(val payload: ByteArray) : WebSocketMessage()

  fun textOrThrow(): String {
    return when (this) {
      is Binary -> error("Binary messages are not expected")
      is Text -> payload
    }
  }

  fun bytesOrThrow(): ByteArray {
    return when (this) {
      is Binary -> payload
      is Text -> error("Text messages are not expected")
    }
  }
}

fun websocketTransportFactory(connect: (SocketStats?) -> Resource<Transport<WebSocketMessage>>): FleetTransportFactory =
  FleetTransportFactory { transportStats ->
    val stats = transportStats?.let { SocketStats.reportToFlow(it) }
    val serializer = TransportMessage.serializer()
    connect(stats).map { wsTransport ->
      val incomingMsg = wsTransport.incoming.map { wsMsg ->
        Json.decodeFromString(serializer, wsMsg.textOrThrow())
      }
      val outgoingMsg = wsTransport.outgoing.map { transportMsg: TransportMessage ->
        WebSocketMessage.Text(Json.encodeToString(serializer, transportMsg))
      }
      Transport(outgoingMsg, incomingMsg)
    }
  }
