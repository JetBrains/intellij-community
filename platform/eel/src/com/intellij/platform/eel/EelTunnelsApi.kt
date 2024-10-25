// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel

import com.intellij.platform.eel.EelTunnelsApi.Arguments.hostAddressBuilder
import com.intellij.platform.eel.EelTunnelsApi.Connection
import com.intellij.platform.eel.impl.HostAddressBuilderImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.coroutineScope
import java.io.IOException
import java.nio.ByteBuffer
import kotlin.time.Duration

/**
 * API for sockets. Use [hostAddressBuilder] to create arguments.
 */
sealed interface EelTunnelsApi {

  /**
   * **For applied usages, consider using [withConnectionToRemotePort]**.
   *
   * Creates a connection to a TCP socket to a named host specified by [address].
   *
   * If the result is [EelNetworkResult.Error], then there was an error during establishment of the connection.
   * Otherwise, the result is [EelNetworkResult.Ok], which means that the connection is ready to use.
   *
   * The connection exists as a pair of channels [Connection.sendChannel] and [Connection.receiveChannel],
   * which allow communicating to a remote server from the IDE side.
   *
   * Packets sent to the channel and received from the channel may be split and/or concatenated.
   * The packets may be split only if their size exceeds [com.intellij.platform.ijent.spi.RECOMMENDED_MAX_PACKET_SIZE].
   *
   * If the connection gets closed from the server, then the channels also get closed in the sense of [SendChannel.close].
   *
   * If an exception happens during sending, then [Connection.receiveChannel] gets closed exceptionally with [RemoteNetworkException].
   *
   * [Connection.sendChannel] can be closed separately with [SendChannel.close]. In this case, the EOF is sent to the server.
   * Note, that [Connection.receiveChannel] is __not__ closed in this case.
   *
   * One should not forget to invoke [Connection.close] when the connection is not needed.
   */
  suspend fun getConnectionToRemotePort(address: HostAddress): EelResult<Connection, EelConnectionError>


  sealed interface ResolvedSocketAddress {
    val port: UShort

    interface V4 : ResolvedSocketAddress {
      val bits: UInt
    }

    interface V6 : ResolvedSocketAddress {
      val higherBits: ULong
      val lowerBits: ULong
    }
  }

  /**
   * Represents an address to a remote host.
   */
  interface HostAddress {

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
       * If the connection could not be established before [timeout], then [EelConnectionError.ConnectionTimeout] would be returned
       * in [EelTunnelsApi.getConnectionToRemotePort].
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
   * Represents a controller for a TCP connection
   */
  interface Connection {

    /**
     * A channel to the server
     */
    // todo: Channel is a bad API here.
    // The client that sends data to the server is also interested in the error that happens during the send.
    // This can be fixed by having a suspend function instead of a channel
    val sendChannel: SendChannel<ByteBuffer>

    /**
     * A channel from the server
     */
    val receiveChannel: ReceiveChannel<ByteBuffer>

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
    class UnknownFailure(error: String) : RemoteNetworkException(error)
  }

  /**
   * **For applied usages, please consider [withConnectionToRemotePort]**.
   *
   * Accepts remote connections to a named host specified by [address].
   *
   * If the result is [EelNetworkResult.Error], then there was an error during creation of the server.
   * Otherwise, the result is [EelNetworkResult.Ok], which means that the server was created successfully.
   *
   * Locally, the server exists as a channel of [Connection]s, which allows imitating a server on the IDE side.
   *
   * Packets sent to the channels and received from the channel may be split and/or concatenated.
   * The packets may be split only if their size exceeds [com.intellij.platform.ijent.spi.RECOMMENDED_MAX_PACKET_SIZE].
   *
   * If the connections get closed, then the channels also get closed in the sense of [SendChannel.close].
   *
   * If an exception happens during sending, then [Connection.sendChannel] gets closed exceptionally with [RemoteNetworkException].
   *
   * [Connection.sendChannel] can be closed separately with [SendChannel.close]. In this case, the EOF is sent to the server.
   * Note, that [Connection.receiveChannel] is __not__ closed in this case.
   *
   * One should not forget to invoke [Connection.close] when the connection is not needed.
   */
  suspend fun getAcceptorForRemotePort(address: HostAddress): EelResult<ConnectionAcceptor, EelConnectionError>

  /**
   * This is a representation of a remote server bound to [boundAddress].
   */
  interface ConnectionAcceptor {
    /**
     * A channel of incoming connections to the remote server.
     * @see Connection
     */
    val incomingConnections: ReceiveChannel<Connection>

    /**
     * A bound local address where the server accepts connections.
     * This address can be useful when the client wants to get a dynamically allocated port.
     */
    val boundAddress: ResolvedSocketAddress

    /**
     * Stops the server from accepting connections.
     */
    suspend fun close()
  }

  companion object Arguments {
    /**
     * Creates a builder for address on the remote host.
     */
    fun hostAddressBuilder(port: UShort): HostAddress.Builder = HostAddressBuilderImpl(port)

    /**
     * Creates a builder for address `localhost:0`.
     * This can be useful in remote port forwarding, as it allocates a random port on the remote host side.
     */
    fun hostAddressBuilder(): HostAddress.Builder = HostAddressBuilderImpl(0u)
  }
}

/**
 * Convenience operator to decompose connection to a pair of channels when needed.
 * @return channel to server
 */
operator fun Connection.component1(): SendChannel<ByteBuffer> = sendChannel

/**
 * Convenience operator to decompose connection to a pair of channels when needed.
 * @return channel from server
 */
operator fun Connection.component2(): ReceiveChannel<ByteBuffer> = receiveChannel

interface EelTunnelsPosixApi : EelTunnelsApi {
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

interface EelTunnelsWindowsApi : EelTunnelsApi

/**
 * Convenience function for working with a connection to a remote server.
 *
 * Example:
 * ```kotlin
 *
 * suspend fun foo() {
 *   EelTunnelsApi.withConnectionToRemotePort("localhost", 8080, {
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
 * @see EelTunnelsApi.getConnectionToRemotePort for more details on the behavior of [Connection]
 */
suspend fun <T> EelTunnelsApi.withConnectionToRemotePort(
  hostAddress: EelTunnelsApi.HostAddress,
  errorHandler: suspend (EelConnectionError) -> T,
  action: suspend CoroutineScope.(Connection) -> T,
): T =
  when (val connectionResult = getConnectionToRemotePort(hostAddress)) {
    is EelResult.Error -> errorHandler(connectionResult.error)
    is EelResult.Ok -> closeWithExceptionHandling({ action(connectionResult.value) }, { connectionResult.value.close() })
  }

private suspend fun <T> closeWithExceptionHandling(action: suspend CoroutineScope.() -> T, close: suspend () -> Unit): T {
  var original: Throwable? = null
  try {
    return coroutineScope {
      action()
    }
  }
  catch (e: Throwable) {
    original = e
    throw e
  }
  finally {
    if (original == null) {
      close()
    }
    else {
      try {
        close()
      }
      catch (e: Throwable) {
        original.addSuppressed(e)
      }
    }
  }
}

suspend fun <T> EelTunnelsApi.withConnectionToRemotePort(
  host: String, port: UShort,
  errorHandler: suspend (EelConnectionError) -> T,
  action: suspend CoroutineScope.(Connection) -> T,
): T = withConnectionToRemotePort(hostAddressBuilder(port).hostname(host).build(), errorHandler, action)

suspend fun <T> EelTunnelsApi.withConnectionToRemotePort(
  remotePort: UShort,
  errorHandler: suspend (EelConnectionError) -> T,
  action: suspend CoroutineScope.(Connection) -> T,
): T = withConnectionToRemotePort("localhost", remotePort, errorHandler, action)


suspend fun <T> EelTunnelsApi.withAcceptorForRemotePort(
  hostAddress: EelTunnelsApi.HostAddress,
  errorHandler: suspend (EelConnectionError) -> T,
  action: suspend CoroutineScope.(EelTunnelsApi.ConnectionAcceptor) -> T,
): T =
  when (val connectionResult = getAcceptorForRemotePort(hostAddress)) {
    is EelResult.Error -> errorHandler(connectionResult.error)
    is EelResult.Ok -> closeWithExceptionHandling({ action(connectionResult.value) }, { connectionResult.value.close() })
  }

/**
 * Represents a common class for all network-related errors appearing during the interaction with IJent or local process
 */
sealed interface EelNetworkError

/**
 * An error that can happen during the creation of a connection to a remote server
 */
interface EelConnectionError : EelNetworkError {
  val message: String

  /**
   * Returned when the remote host cannot create an object of a socket.
   */
  interface SocketAllocationError : EelConnectionError

  /**
   * Returned when there is a problem with resolve of the hostname.
   */
  interface ResolveFailure : EelConnectionError

  /**
   * Returned when there was a problem with establishing a connection to a resolved server
   */
  interface ConnectionProblem : EelConnectionError

  /**
   * Unknown failure during a connection establishment
   */
  interface UnknownFailure : EelConnectionError
}
