// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.impl.local.tunnels

import com.intellij.platform.eel.EelConnectionError
import com.intellij.platform.eel.EelResult
import com.intellij.platform.eel.EelTunnelsApi
import com.intellij.platform.eel.impl.NetworkError
import com.intellij.platform.eel.impl.NetworkOk
import com.intellij.platform.eel.impl.UnknownFailure
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.InetSocketAddress
import java.nio.channels.SocketChannel


internal suspend fun getConnectionToRemotePortImpl(address: EelTunnelsApi.HostAddress): EelResult<EelTunnelsApi.Connection, EelConnectionError> =
  withContext(Dispatchers.IO) {
    val socketChannel = SocketChannel.open()
    val connKiller = async {
      delay(address.timeout)
      socketChannel.close()
    }
    return@withContext try {
      // TODO: Obey IP4/IPV6 order
      socketChannel.connect(InetSocketAddress(address.hostname, address.port.toInt()))
      NetworkOk(SocketAdapter(socketChannel))
    }
    catch (e: IOException) {
      NetworkError(UnknownFailure(e.toString()))
    }
    finally {
      connKiller.cancel()
    }
  }

internal suspend fun getAcceptorForRemotePortImpl(address: EelTunnelsApi.HostAddress): EelResult<EelTunnelsApi.ConnectionAcceptor, EelConnectionError> {
  TODO("Not implemented (yet)")
}