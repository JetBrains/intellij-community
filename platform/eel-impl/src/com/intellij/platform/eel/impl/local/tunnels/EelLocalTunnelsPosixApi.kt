// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.impl.local.tunnels

import com.intellij.platform.eel.*

internal object EelLocalTunnelsPosixApi : EelTunnelsPosixApi {
  override suspend fun listenOnUnixSocket(path: EelTunnelsPosixApi.CreateFilePath): EelTunnelsPosixApi.ListenOnUnixSocketResult {
    TODO("Not yet implemented")
  }


  override suspend fun getConnectionToRemotePort(address: EelTunnelsApi.HostAddress, configureSocketBeforeConnection: ConfigurableClientSocket.() -> Unit): EelResult<EelTunnelsApi.Connection, EelConnectionError> =
    getConnectionToRemotePortImpl(address, configureSocketBeforeConnection)

  override suspend fun getAcceptorForRemotePort(address: EelTunnelsApi.HostAddress, configureServerSocket: ConfigurableSocket.() -> Unit): EelResult<EelTunnelsApi.ConnectionAcceptor, EelConnectionError> =
    getAcceptorForRemotePortImpl(address, configureServerSocket)

}