// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.impl.local.tunnels

import com.intellij.platform.eel.*
import com.intellij.platform.eel.EelTunnelsApi.*

internal object EelLocalTunnelsPosixApi : EelTunnelsPosixApi {
  override suspend fun listenOnUnixSocket(path: CreateFilePath): ListenOnUnixSocketResult {
    TODO("Not yet implemented")
  }


  override suspend fun getConnectionToRemotePort(address: HostAddress, configureSocketBeforeConnection: ConfigurableClientSocket.() -> Unit): EelResult<Connection, EelConnectionError> =
    getConnectionToRemotePortImpl(address, configureSocketBeforeConnection)

  override suspend fun getAcceptorForRemotePort(address: HostAddress, configureServerSocket: ConfigurableSocket.() -> Unit): EelResult<ConnectionAcceptor, EelConnectionError> =
    getAcceptorForRemotePortImpl(address, configureServerSocket)

}