// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.rpc.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

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
