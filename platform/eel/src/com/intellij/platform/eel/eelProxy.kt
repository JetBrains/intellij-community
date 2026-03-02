// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Experimental
@file:OptIn(EelDelicateApi::class)

package com.intellij.platform.eel

import com.intellij.platform.eel.channels.EelDelicateApi
import com.intellij.platform.eel.impl.EelProxyImpl
import org.jetbrains.annotations.ApiStatus
import java.util.ServiceLoader

// TODO Move to a separate package along with EelTunnelsApi

/**
 * A generic function for setting up a proxy server using Eel.
 * It's not obligatory to use it for proxying, but it can save time and simplify the code.
 *
 * A general usage example:
 * ```kotlin
 * val proxy = try {
 *   eelProxy()
 *     .localAcceptorFactory { ... }
 *     .remoteConnectionFactory { ... }
 *     .eelIt()
 * }
 * catch (e: EelConnectionError) { ... }
 *
 * println("Proxy server is running on ${proxy.localAcceptor.hostAddress}")
 * val job = launch {
 *   proxy.runForever()
 * }
 * ...
 * job.cancel()  // When the proxy is no longer needed. All connections get closed.
 * ```
 *
 * See also [com.intellij.platform.eel.provider.utils.acceptOnTcpPort] and [com.intellij.platform.eel.provider.utils.connectToTcpPort],
 * they help to set up a proxy server that listens on a local machine and forwards requests to a remote machine:
 *
 * An example of a proxy server that listens locally (i.e., where the IDE runs) and forwards connections remotely:
 * ```kotlin
 * eelProxy()
 *   .acceptOnTcpPort(localEel, port = 1234u)
 *   .connectToTcpPort(remoteEel, port = 5678u)
 *   .eelIt()
 * ```
 *
 * An example of a proxy server that accepts connections remotely and forwards them to a local machine:
 * ```kotlin
 * eelProxy()
 *   .acceptOnTcpPort(remoteEel, port = 1234u)
 *   .connectToTcpPort(localEel, port = 5678u)
 *   .eelIt()
 * ```
 *
 * @throws EelConnectionError on a failure to create the acceptor.
 */
@ThrowsChecked(EelConnectionError::class)
@ApiStatus.Experimental
suspend fun eelProxy(@GeneratedBuilder opts: EelTunnelsApiRunProxyOpts): EelProxy {
  val impl = ServiceLoader.load(EelProxyImpl::class.java).single()
  return impl.eelProxyImpl(
    acceptorFactory = opts.acceptorFactory,
    connectionFactory = opts.connectionFactory,
    onConnection = opts.onConnection,
    onConnectionClosed = opts.onConnectionClosed,
    onConnectionError = opts.onConnectionError,
    debugLabel = opts.debugLabel,
    acceptorInfo = opts._acceptorInfo,
    connectorInfo = opts._connectorInfo,
    fakeProxyPossible = opts._fakeProxyPossible,
  )
}

/**
 * This entity indicates that the proxy server is ready to accept connections. See [eelProxy] for example usage.
 */
@ApiStatus.Experimental
@ApiStatus.NonExtendable
interface EelProxy {
  /**
   * The proxy server gets new connections from acceptor.
   *
   * The acceptor is automatically closed when [runForever] stops (by cancellation or by an exception).
   *
   * It's also possible to close the acceptor manually to stop accepting new connections without terminating existing ones.
   */
  val acceptor: EelTunnelsApi.ConnectionAcceptor

  /**
   * The proxy server starts listening only at this point.
   *
   * Blocks the current coroutine until it is canceled or until [acceptor] is explicitly closed and all connections are closed too.
   *
   * The function is not idempotent. Calling it more than once leads to undefined behavior.
   */
  suspend fun runForever()
}

@ApiStatus.Experimental
interface EelTunnelsApiRunProxyOpts {
  /**
   * This function is called exactly once. Its result is stored in [EelProxy.acceptor].
   */
  val acceptorFactory: @ThrowsChecked(EelConnectionError::class) suspend () -> EelTunnelsApi.ConnectionAcceptor
    get() = { error("Forgot to set localAcceptor") }

  /**
   * This function is called every time the acceptor gets a new connection.
   *
   * See also [onConnection], [onConnectionClosed], [onConnectionError]
   */
  val connectionFactory: @ThrowsChecked(EelConnectionError::class) suspend () -> EelTunnelsApi.Connection
    get() = { error("Forgot to set remoteConnectionFactory") }

  /**
   * Receives connections instantiated by [connectionFactory].
   *
   * The function is called before transferring any data.
   * It must work as fast as possible. Otherwise, it may prevent accepting new connections.
   *
   * The handler is not supposed to close the connection. If it happens, the behavior is undefined.
   */
  @EelDelicateApi
  val onConnection: ((EelTunnelsApi.Connection) -> Unit)? get() = null

  /**
   * Called when the connection is closed.
   */
  val onConnectionClosed: ((EelTunnelsApi.Connection) -> Unit)? get() = null

  /**
   * Receives errors thrown by [connectionFactory].
   *
   * It must work as fast as possible. Otherwise, it may prevent accepting new connections.
   */
  @EelDelicateApi
  val onConnectionError: ((EelConnectionError) -> Unit)? get() = null

  /**
   * Some text that may be visible in logs and thread dumps.
   */
  val debugLabel: String?
    get() = null

  /** For internal use inside Eel only. */
  @Suppress("PropertyName")
  @get:ApiStatus.Internal
  val _acceptorInfo: Pair<EelTunnelsApi, Any?>? get() = null

  /** For internal use inside Eel only. */
  @Suppress("PropertyName")
  @get:ApiStatus.Internal
  val _connectorInfo: Pair<EelTunnelsApi, Any?>? get() = null

  /** For internal use inside Eel only. */
  @Suppress("PropertyName")
  @get:ApiStatus.Internal
  val _fakeProxyPossible: Boolean get() = true
}