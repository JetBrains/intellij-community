// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent

import com.intellij.platform.ijent.IjentNetworkResult.Ok
import com.intellij.platform.ijent.IjentTunnelsApi.Connection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.coroutineScope
import java.io.IOException
import kotlin.time.Duration

/**
 * Methods for launching tunnels for TCP sockets, Unix sockets, etc.
 */
sealed interface IjentTunnelsApi {

  /**
   * **This is a delicate API, for applied usages, please consider [withConnectionToRemotePort]**.
   *
   * Creates a connection to a TCP socket to a named host specified by [address].
   *
   * If the result is [IjentNetworkResult.Error], then there was an error during establishment of the connection.
   * Otherwise, the result is [IjentNetworkResult.Ok], which means that the connection is ready to use.
   *
   * The connection exists as a pair of channels [Connection.channelToServer] and [Connection.channelFromServer],
   * which allow communicating to a remote server from the IDE side.
   *
   * If the connection gets closed from the server, then the channels also get closed in the sense of [SendChannel.close].
   *
   * If an exception happens during sending, then [Connection.channelFromServer] gets closed exceptionally with [RemoteNetworkException].
   *
   * [Connection.channelToServer] can be closed separately with [SendChannel.close]. In this case, the EOF is sent to the server.
   * Note, that [Connection.channelFromServer] is __not__ closed in this case.
   *
   * One should not forget to invoke [Connection.close] when the connection is not needed.
   */
  suspend fun getConnectionToRemotePort(address: HostAddress): IjentNetworkResult<Connection, IjentConnectionError>

  /**
   * Creates a builder for address on the remote host.
   */
  fun hostAddressBuilder(port: UShort): HostAddress.Builder

  interface HostAddress {
    interface Builder {
      fun hostname(hostname: String): Builder
      fun preferIPv4(): Builder
      fun preferIPv6(): Builder
      fun preferOSDefault(): Builder
      fun build(): HostAddress
    }
  }


  /**
   * Represents a controller for a remote connection
   */
  interface Connection {
    /**
     * A channel to the remote server
     */
    val channelToServer: SendChannel<ByteArray>

    /**
     * A channel from the remote server
     */
    val channelFromServer: ReceiveChannel<ByteArray>

    /**
     * Sets the size of send buffer of the socket
     * @see java.net.SocketOptions.SO_SNDBUF
     */
    suspend fun setSendBufferSize(size: UInt)

    /**
     * Sets the receive buffer size of the socket
     * @see java.net.SocketOptions.SO_RCVBUF
     */
    suspend fun setReceiveBufferSize(size: UInt)

    /**
     * Sets the keep alive option for the socket
     * @see java.net.SocketOptions.SO_KEEPALIVE
     */
    suspend fun setKeepAlive(keepAlive: Boolean)

    /**
     * Sets the possibility to reuse address of the socket
     * @see java.net.SocketOptions.SO_REUSEADDR
     */
    suspend fun setReuseAddr(reuseAddr: Boolean)

    /**
     * Sets linger timeout for the socket
     * @see java.net.SocketOptions.SO_LINGER
     */
    suspend fun setLinger(lingerInterval: Duration)

    /**
     * Disables pending data until acknowledgement
     * @see java.net.SocketOptions.TCP_NODELAY
     */
    suspend fun setNoDelay(noDelay: Boolean)

    /**
     * Closes the connection to the socket.
     */
    suspend fun close()
  }

  sealed class RemoteNetworkException(message: String) : IOException(message) {
    constructor() : this("")

    class ConnectionReset : RemoteNetworkException()
    class ConnectionAborted : RemoteNetworkException()
    class UnknownFailure(error: String) : RemoteNetworkException(error)
  }
}

/**
 * Convenience operator to decompose connection to a pair of channels when needed.
 * @return channel to server
 */
operator fun Connection.component1(): SendChannel<ByteArray> = channelToServer

/**
 * Convenience operator to decompose connection to a pair of channels when needed.
 * @return channel from server
 */
operator fun Connection.component2(): ReceiveChannel<ByteArray> = channelFromServer

interface IjentTunnelsPosixApi : IjentTunnelsApi {
  /**
   * Creates a remote UNIX socket forwarding, i.e. IJent listens waits for a connection on the remote machine, and when the connection
   * is accepted, the IDE communicates to the remote client via a pair of Kotlin channels.
   *
   * The call accepts only one connection. If multiple connections should be accepted, the function is supposed to be called in a loop:
   * ```kotlin
   * val ijent: IjentApi = ijentApiFactory()
   *
   * val (socketPath, tx, rx) = listenOnUnixSocket(CreateFilePath.MkTemp(prefix = "ijent-", suffix = ".sock"))
   * println(socketPath) // /tmp/ijent-12345678.sock
   * launch {
   *   handleConnection(tx, rx)
   * }
   * while (true) {
   *   val (_, tx, rx) = listenOnUnixSocket(CreateFilePath.Fixed(socketPath))
   *   launch {
   *     handleConnection(tx, rx)
   *   }
   * }
   * ```
   */
  suspend fun listenOnUnixSocket(path: CreateFilePath = CreateFilePath.MkTemp()): ListenOnUnixSocketResult

  data class ListenOnUnixSocketResult(
    val unixSocketPath: String,
    // TODO Avoid excessive byte arrays copying.
    val tx: SendChannel<ByteArray>,
    val rx: ReceiveChannel<ByteArray>,
  )

  sealed interface CreateFilePath {
    data class Fixed(val path: String) : CreateFilePath

    /** When [directory] is empty, the usual tmpdir is used. */
    data class MkTemp(val directory: String = "", val prefix: String = "", val suffix: String = "") : CreateFilePath
  }
}

interface IjentTunnelsWindowsApi : IjentTunnelsApi

/**
 * Convenience function for working with a connection to a remote server.
 *
 * Example:
 * ```kotlin
 *
 * suspend fun foo() {
 *   ijentTunnelsApi.withConnectionToRemotePort("localhost", 8080, {
 *     myErrorReporter.report(it)
 *   }) { (channelTo, channelFrom) ->
 *     handleConnection(channelTo, channelFrom)
 *   }
 * }
 *
 * ```
 *
 * If the connection could not be established, then [errorHandler] is invoked.
 * Otherwise, [action] is invoked. The connection gets automatically closed when [action] finishes.
 *
 * @see com.intellij.platform.ijent.IjentTunnelsApi.getConnectionToRemotePort for more details on the behavior of [Connection]
 */
suspend fun <T> IjentTunnelsApi.withConnectionToRemotePort(
  host: String, port: UShort,
  errorHandler: suspend (IjentConnectionError) -> T,
  action: suspend CoroutineScope.(Connection) -> T): T =
  when (val connectionResult = getConnectionToRemotePort(hostAddressBuilder(port).hostname(host).build())) {
    is IjentNetworkResult.Error -> errorHandler(connectionResult.error)
    is Ok -> try {
      coroutineScope { action(connectionResult.value) }
    }
    finally {
      connectionResult.value.close()
    }
  }

suspend fun <T> IjentTunnelsApi.withConnectionToRemotePort(
  remotePort: UShort,
  errorHandler: suspend (IjentConnectionError) -> T,
  action: suspend CoroutineScope.(Connection) -> T): T = withConnectionToRemotePort("localhost", remotePort, errorHandler, action)

sealed interface IjentNetworkError

sealed interface IjentNetworkResult<out T, out E : IjentNetworkError> {
  interface Ok<out T> : IjentNetworkResult<T, Nothing> {
    val value: T
  }

  interface Error<out E : IjentNetworkError> : IjentNetworkResult<Nothing, E> {
    val error: E
  }
}

interface IjentConnectionError : IjentNetworkError {

  data object AmbiguousAddress : IjentConnectionError

  /**
   * Used when resolve of remote address failed
   */
  @JvmInline
  value class SocketCreationFailure(val err: String) : IjentConnectionError

  /**
   * Used when resolve of remote address failed
   */
  object HostUnreachable : IjentConnectionError

  /**
   * Used when resolve of remote address failed
   */
  object ConnectionRefused : IjentConnectionError

  /**
   * Used when resolve of remote address failed
   */
  @JvmInline
  value class ResolveFailure(val err: String) : IjentConnectionError

  /**
   * Unknown failure during a connection establishment
   */
  @JvmInline
  value class UnknownFailure(val err: String) : IjentConnectionError
}


