// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent

import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.ijent.IjentNetworkResult.Ok
import com.intellij.platform.ijent.IjentTunnelsApi.Connection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.coroutineScope
import java.io.IOException
import java.nio.ByteBuffer
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
   * Packets sent to the channel and received from the channel may be split and/or concatenated.
   * The packets may be split only if their size exceeds [com.intellij.platform.ijent.spi.RECOMMENDED_MAX_PACKET_SIZE].
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
  @Throws(IjentUnavailableException::class)
  suspend fun getConnectionToRemotePort(address: HostAddress): IjentNetworkResult<Connection, IjentConnectionError>

  /**
   * Creates a builder for address on the remote host.
   */
  fun hostAddressBuilder(port: UShort): HostAddress.Builder

  /**
   * Represents an address to a remote host.
   */
  interface HostAddress {
    /**
     * A builder class for remote host address.
     */
    interface Builder {

      /**
       * Sets the hostname for a remote host.
       * The hostname will be resolved remotely.
       *
       * By default, the hostname is `localhost`
       */
      fun hostname(hostname: String): Builder

      /**
       * If [hostname] is resolved to an IPv4 address, then it is used.
       *
       * Overrides [preferIPv6] and [preferOSDefault]
       */
      fun preferIPv4(): Builder

      /**
       * If [hostname] is resolved to an IPv6 address, then it is used.
       * Overrides [preferIPv4] and [preferOSDefault]
       */
      fun preferIPv6(): Builder

      /**
       * [hostname] is resolved according to the settings on the remote host.
       *
       * Overrides [preferIPv4] and [preferIPv6]. This is the default option.
       */
      fun preferOSDefault(): Builder

      /**
       * Sets timeout for connecting to remote host.
       * If the connection could not be established before [timeout], then [IjentConnectionError.ConnectionTimeout] would be returned
       * in [IjentTunnelsApi.getConnectionToRemotePort].
       *
       * Default value: 10 seconds.
       * The recognizable granularity is milliseconds.
       */
      fun connectionTimeout(timeout: Duration): Builder

      /**
       * Builds a remote host address object.
       */
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
    val channelToServer: SendChannel<ByteBuffer>

    /**
     * A channel from the remote server
     */
    val channelFromServer: ReceiveChannel<ByteBuffer>

    /**
     * Sets the size of send buffer of the socket
     * @see java.net.SocketOptions.SO_SNDBUF
     */
    @Throws(IjentUnavailableException::class)
    suspend fun setSendBufferSize(size: UInt)

    /**
     * Sets the receive buffer size of the socket
     * @see java.net.SocketOptions.SO_RCVBUF
     */
    @Throws(IjentUnavailableException::class)
    suspend fun setReceiveBufferSize(size: UInt)

    /**
     * Sets the keep alive option for the socket
     * @see java.net.SocketOptions.SO_KEEPALIVE
     */
    @Throws(IjentUnavailableException::class)
    suspend fun setKeepAlive(keepAlive: Boolean)

    /**
     * Sets the possibility to reuse address of the socket
     * @see java.net.SocketOptions.SO_REUSEADDR
     */
    @Throws(IjentUnavailableException::class)
    suspend fun setReuseAddr(reuseAddr: Boolean)

    /**
     * Sets linger timeout for the socket
     * @see java.net.SocketOptions.SO_LINGER
     */
    @Throws(IjentUnavailableException::class)
    suspend fun setLinger(lingerInterval: Duration)

    /**
     * Disables pending data until acknowledgement
     * @see java.net.SocketOptions.TCP_NODELAY
     */
    @Throws(IjentUnavailableException::class)
    suspend fun setNoDelay(noDelay: Boolean)

    /**
     * Closes the connection to the socket.
     */
    @Throws(IjentUnavailableException::class)
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
operator fun Connection.component1(): SendChannel<ByteBuffer> = channelToServer

/**
 * Convenience operator to decompose connection to a pair of channels when needed.
 * @return channel from server
 */
operator fun Connection.component2(): ReceiveChannel<ByteBuffer> = channelFromServer

interface IjentTunnelsPosixApi : IjentTunnelsApi {
  /**
   * Creates a remote UNIX socket forwarding. IJent listens for a connection on the remote machine, and when the connection
   * is accepted, the IDE communicates to the remote client via a pair of Kotlin channels.
   *
   * Packets sent to the channel and received from the channel may be split and/or concatenated.
   * The packets may be split only if their size exceeds [com.intellij.platform.ijent.spi.RECOMMENDED_MAX_PACKET_SIZE].
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
  @Throws(IjentUnavailableException::class)
  suspend fun listenOnUnixSocket(path: CreateFilePath = CreateFilePath.MkTemp()): ListenOnUnixSocketResult

  data class ListenOnUnixSocketResult(
    val unixSocketPath: String,
    val tx: SendChannel<ByteBuffer>,
    val rx: ReceiveChannel<ByteBuffer>,
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
@Throws(IjentUnavailableException::class)
suspend fun <T> IjentTunnelsApi.withConnectionToRemotePort(
  hostAddress: IjentTunnelsApi.HostAddress,
  errorHandler: suspend (IjentConnectionError) -> T,
  action: suspend CoroutineScope.(Connection) -> T,
): T =
  when (val connectionResult = getConnectionToRemotePort(hostAddress)) {
    is IjentNetworkResult.Error -> errorHandler(connectionResult.error)
    is Ok -> try {
      coroutineScope { action(connectionResult.value) }
    }
    finally {
      connectionResult.value.close()
    }
  }

@Throws(IjentUnavailableException::class)
suspend fun <T> IjentTunnelsApi.withConnectionToRemotePort(
  host: String, port: UShort,
  errorHandler: suspend (IjentConnectionError) -> T,
  action: suspend CoroutineScope.(Connection) -> T,
): T = withConnectionToRemotePort(hostAddressBuilder(port).hostname(host).build(), errorHandler, action)

@Throws(IjentUnavailableException::class)
suspend fun <T> IjentTunnelsApi.withConnectionToRemotePort(
  remotePort: UShort,
  errorHandler: suspend (IjentConnectionError) -> T,
  action: suspend CoroutineScope.(Connection) -> T,
): T = withConnectionToRemotePort("localhost", remotePort, errorHandler, action)

/**
 * Represents a common class for all network-related errors appearing during the interaction with IJent
 */
sealed interface IjentNetworkError

/**
 * Represents a result of a network operation
 */
sealed interface IjentNetworkResult<out T, out E : IjentNetworkError> {
  /**
   * Used when a network operation completed successfully
   */
  interface Ok<out T> : IjentNetworkResult<T, Nothing> {
    val value: T
  }

  /**
   * Used when a network operation completed with an error
   */
  interface Error<out E : IjentNetworkError> : IjentNetworkResult<Nothing, E> {
    val error: E
  }
}

/**
 * An error that can happen during the creation of a connection to a remote server
 */
interface IjentConnectionError : IjentNetworkError {
  val message: @NlsSafe String

  data object ConnectionTimeout : IjentConnectionError {
    override val message: @NlsSafe String = "Connection could not be established because of timeout"
  }

  /**
   * Returned when a hostname on the remote server was resolved to multiple different addresses.
   */
  data object AmbiguousAddress : IjentConnectionError {
    override val message: String = "Hostname could not be resolved uniquely"
  }

  /**
   * Returned when a socket could not be created because of an OS error.
   */
  @JvmInline
  value class SocketCreationFailure(override val message: @NlsSafe String) : IjentConnectionError

  /**
   * Returned when resolve of remote address failed during the creation of a socket.
   */
  object HostUnreachable : IjentConnectionError {
    override val message: @NlsSafe String = "Remote host is unreachable"
  }

  /**
   * Returned when the remote server does not accept connections.
   */
  object ConnectionRefused : IjentConnectionError {
    override val message: @NlsSafe String = "Connection was refused by remote server"
  }

  /**
   * Returned when hostname could not be resolved.
   */
  @JvmInline
  value class ResolveFailure(override val message: @NlsSafe String) : IjentConnectionError

  /**
   * Unknown failure during a connection establishment
   */
  @JvmInline
  value class UnknownFailure(override val message: @NlsSafe String) : IjentConnectionError
}


