// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.impl.local.tunnels

import com.intellij.platform.eel.ConfigurableClientSocket
import com.intellij.platform.eel.EelTunnelsApi
import com.intellij.platform.eel.channels.EelReceiveChannel
import com.intellij.platform.eel.channels.EelSendChannel
import com.intellij.platform.eel.provider.utils.asEelChannel
import com.intellij.platform.eel.provider.utils.consumeAsEelChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.channels.SocketChannel

internal class SocketAdapter(private val channel: SocketChannel) : EelTunnelsApi.Connection {
  private val socket = channel.socket()

  override fun toString(): String = "SocketAdapter[$channel]"

  override val sendChannel: EelSendChannel = channel.asEelChannel()
  override val receiveChannel: EelReceiveChannel = channel.consumeAsEelChannel()

  override suspend fun configureSocket(block: suspend ConfigurableClientSocket.() -> Unit) {
    block(ConfigurableClientSocketImpl(socket))
  }


  override suspend fun close() {
    if (socket.isClosed) return
    withContext(Dispatchers.IO) {
      try {
        if (socket.soLinger != 0) {
          // We do not export these methods, but calling them is a right thing to do (except for linger=0 which means "die asap")
          socket.shutdownOutput()
          socket.shutdownInput()
        }
      }
      catch (_: IOException) {
        // Ignored.
      }
      socket.close()
    }
  }
}
