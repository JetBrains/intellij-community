// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.impl.local.tunnels

import com.intellij.platform.eel.EelConnectionError
import com.intellij.platform.eel.EelResult
import com.intellij.platform.eel.EelTunnelsApi
import com.intellij.platform.eel.EelTunnelsPosixApi
import com.intellij.platform.eel.EelTunnelsWindowsApi

internal object EelLocalTunnelsWindowsApi : EelTunnelsWindowsApi {

  override suspend fun getConnectionToRemotePort(address: EelTunnelsApi.HostAddress): EelResult<EelTunnelsApi.Connection, EelConnectionError> =
    getConnectionToRemotePortImpl(address)

  override suspend fun getAcceptorForRemotePort(address: EelTunnelsApi.HostAddress): EelResult<EelTunnelsApi.ConnectionAcceptor, EelConnectionError> =
    getAcceptorForRemotePortImpl(address)

}