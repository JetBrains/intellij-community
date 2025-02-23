// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.impl.local.tunnels

import com.intellij.platform.eel.EelTunnelsApi
import com.intellij.platform.eel.channels.EelReceiveChannel
import com.intellij.platform.eel.channels.EelSendChannel
import com.intellij.platform.eel.provider.utils.asEelChannel
import com.intellij.platform.eel.provider.utils.consumeAsEelChannel
import java.io.IOException
import java.nio.channels.SocketChannel
import kotlin.time.Duration

internal class SocketAdapter(private val channel: SocketChannel) : EelTunnelsApi.Connection {
  private val socket = channel.socket()


  override val sendChannel: EelSendChannel<IOException> = channel.asEelChannel()
  override val receiveChannel: EelReceiveChannel<IOException> = channel.consumeAsEelChannel()

  override suspend fun setSendBufferSize(size: UInt) {
    socket.sendBufferSize = size.toInt()
  }

  override suspend fun setReceiveBufferSize(size: UInt) {
    socket.receiveBufferSize = size.toInt()
  }

  override suspend fun setKeepAlive(keepAlive: Boolean) {
    socket.keepAlive = keepAlive
  }

  override suspend fun setReuseAddr(reuseAddr: Boolean) {
    socket.reuseAddress = reuseAddr
  }

  override suspend fun setLinger(lingerInterval: Duration) {
    val seconds = lingerInterval.inWholeSeconds
    if (seconds == 0L) {
      socket.setSoLinger(false, 0)
    }
    else {
      socket.setSoLinger(true, seconds.toInt())
    }
  }

  override suspend fun setNoDelay(noDelay: Boolean) {
    socket.tcpNoDelay = noDelay
  }

  override suspend fun close() {
    socket.shutdownOutput()
    socket.shutdownInput()
    socket.close()
  }
}