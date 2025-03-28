// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.impl.local.tunnels

import com.intellij.platform.eel.ConfigurableClientSocket
import com.intellij.platform.eel.ConfigurableSocket
import java.net.ServerSocket
import java.net.Socket
import kotlin.time.Duration

internal class ConfigurableServerSocketImpl(private val socket: ServerSocket) : ConfigurableSocket {

  override suspend fun setReuseAddr(reuseAddr: Boolean) {
    socket.reuseAddress = reuseAddr
  }

  override suspend fun setReceiveBufferSize(size: UInt) {
    socket.receiveBufferSize = size.toInt()
  }

}

internal class ConfigurableClientSocketImpl(private val socket: Socket) : ConfigurableClientSocket {
  override suspend fun setSendBufferSize(size: UInt) {
    socket.sendBufferSize = size.toInt()
  }

  override suspend fun setKeepAlive(keepAlive: Boolean) {
    socket.keepAlive = keepAlive
  }

  override suspend fun setNoDelay(noDelay: Boolean) {
    socket.tcpNoDelay = noDelay
  }

  override suspend fun setLinger(lingerInterval: Duration?) {
    socket.setSoLinger(lingerInterval != null, lingerInterval?.inWholeSeconds?.toInt() ?: 0);
  }

  override suspend fun setReuseAddr(reuseAddr: Boolean) {
    socket.reuseAddress = reuseAddr
  }

  override suspend fun setReceiveBufferSize(size: UInt) {
    socket.receiveBufferSize = size.toInt()
  }
}