// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.impl.local.tunnels

import com.intellij.platform.eel.ConfigurableClientSocket
import com.intellij.platform.eel.ConfigurableSocket
import java.net.ServerSocket
import java.net.Socket

internal class ConfigurableServerSocketImpl(private val socket: ServerSocket) : ConfigurableSocket {

  override suspend fun setReuseAddr(reuseAddr: Boolean) {
    socket.reuseAddress = reuseAddr
  }

}

internal class ConfigurableClientSocketImpl(private val socket: Socket) : ConfigurableClientSocket {

  override suspend fun setNoDelay(noDelay: Boolean) {
    socket.tcpNoDelay = noDelay
  }

  override suspend fun setReuseAddr(reuseAddr: Boolean) {
    socket.reuseAddress = reuseAddr
  }

}