// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.impl

import com.intellij.platform.eel.EelConnectionError
import com.intellij.platform.eel.EelProxy
import com.intellij.platform.eel.EelTunnelsApi
import com.intellij.platform.eel.ThrowsChecked
import org.jetbrains.annotations.ApiStatus

/**
 * Eel implementation detail, do not use
 */
@ApiStatus.Internal
@ApiStatus.NonExtendable
fun interface EelProxyImpl {

  @ThrowsChecked(EelConnectionError::class)
  suspend fun eelProxyImpl(
    acceptorFactory: @ThrowsChecked(EelConnectionError::class) suspend () -> EelTunnelsApi.ConnectionAcceptor,
    connectionFactory: @ThrowsChecked(EelConnectionError::class) suspend () -> EelTunnelsApi.Connection,
    onConnection: ((EelTunnelsApi.Connection) -> Unit)?,
    onConnectionClosed: ((EelTunnelsApi.Connection) -> Unit)?,
    onConnectionError: ((EelConnectionError) -> Unit)?,
    debugLabel: String?,
    acceptorInfo: Pair<EelTunnelsApi, Any?>?,
    connectorInfo: Pair<EelTunnelsApi, Any?>?,
    fakeProxyPossible: Boolean,
  ): EelProxy
}