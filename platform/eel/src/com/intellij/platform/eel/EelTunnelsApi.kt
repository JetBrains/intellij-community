// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel

import com.intellij.platform.eel.EelTunnelsApi.Connection
import com.intellij.platform.eel.channels.EelReceiveChannel
import com.intellij.platform.eel.channels.EelSendChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.coroutineScope
import org.jetbrains.annotations.CheckReturnValue
import java.io.IOException
import kotlin.jvm.Throws
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * API for sockets. Use [hostAddressBuilder] to create arguments.
 */
sealed interface EelTunnelsApi {
  /**
   * Creates a remote UNIX socket forwarding. IJent listens for a connection on the remote machine, and when the connection
   * is accepted, the IDE communicates to the remote client via a pair of Kotlin channels.
   *
   * Packets sent to the channel and received from the channel may be split and/or concatenated.
   * The packets may be split only if their size exceeds [com.intellij.platform.ijent.spi.RECOMMENDED_MAX_PACKET_SIZE].
   *
   * Local implementation should work for **nix and all modern Windows.
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
    val tx: EelSendChannel,
    val rx: EelReceiveChannel,
  )

  sealed interface CreateFilePath {
    data class Fixed(val path: String) : CreateFilePath

    /** When [directory] is empty, the usual tmpdir is used. */
    data class MkTemp(val directory: String = "", val prefix: String = "", val suffix: String = "") : CreateFilePath
  }

  /**
   * **For applied usages, consider using [withConnectionToRemotePort]**.
   *
   * Creates a connection to a TCP socket to a named host specified by [address].
   *
   * If an error occurs during establishment of the connection, an [EelConnectionError] will be thrown.
   * Otherwise, a [Connection] object is returned, which means that the connection is ready to use.
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
   *
   * To configure a socket before connection use [configureSocketBeforeConnection]. After that, use [Connection.configureSocket]
   */
  @Throws(EelConnectionError::class)
  suspend fun getConnectionToRemotePort(@GeneratedBuilder args: GetConnectionToRemotePortArgs): Connection

  interface GetConnectionToRemotePortArgs : HostAddress {
    val configureSocketBeforeConnection: ConfigurableClientSocket.() -> Unit get() = {}
  }


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
    val port: UShort get() = 0u  // TODO Split into two interfaces
    val hostname: String get() = "localhost"

    /**
     * @see [Builder.preferIPv4]
     */
    val protocolPreference: EelIpPreference get() = EelIpPreference.USE_SYSTEM_DEFAULT

    /**
     * @see [Builder.connectionTimeout]
     */
    val timeout: Duration get() = 10.seconds

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
       * If the connection could not be established before [timeout], then [EelConnectionError.ConnectionTimeout] would be thrown
       * by [EelTunnelsApi.getConnectionToRemotePort].
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

    companion object {
      /**
       * Creates a builder for address on the remote host.
       */
      fun Builder(port: UShort): Builder = HostAddressBuilderImpl(port)

      /**
       * Creates a builder for address `localhost:0`.
       * This can be useful in remote port forwarding, as it allocates a random port on the remote host side.
       */
      fun Builder(): Builder = HostAddressBuilderImpl(0u)
    }
  }


  /**
   * Represents a controller for a TCP connection
   */
  interface Connection {

    /**
     * A channel to the server
     */
    val sendChannel: EelSendChannel

    /**
     * A channel from the server
     */
    val receiveChannel: EelReceiveChannel

    /**
     * Configure various socket options
     */
    suspend fun configureSocket(block: suspend ConfigurableClientSocket.() -> Unit)

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
   * If an error occurs during creation of the server, an [EelConnectionError] will be thrown.
   * Otherwise, a [ConnectionAcceptor] object is returned, which means that the server was created successfully.
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
  @Throws(EelConnectionError::class)
  suspend fun getAcceptorForRemotePort(@GeneratedBuilder args: GetAcceptorForRemotePort): ConnectionAcceptor

  interface GetAcceptorForRemotePort : HostAddress {
    // TODO Make it look and feel like all other builders.
    val configureServerSocket: ConfigurableSocket.() -> Unit get() = {}
  }

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
}

/**
 * Socket configuration valid both for server and client socket
 */
interface ConfigurableSocket {
  /**
   * Sets the possibility to reuse address of the socket
   * @see java.net.SocketOptions.SO_REUSEADDR
   */
  suspend fun setReuseAddr(reuseAddr: Boolean)
}

/**
 * Client only socket options
 */
interface ConfigurableClientSocket : ConfigurableSocket {
  /**
   * Disables pending data until acknowledgement
   * @see java.net.SocketOptions.TCP_NODELAY
   */
  suspend fun setNoDelay(noDelay: Boolean)
}

/**
 * Convenience operator to decompose connection to a pair of channels when needed.
 * @return channel to server
 */
operator fun Connection.component1(): EelSendChannel = sendChannel

/**
 * Convenience operator to decompose connection to a pair of channels when needed.
 * @return channel from server
 */
operator fun Connection.component2(): EelReceiveChannel = receiveChannel

interface EelTunnelsPosixApi : EelTunnelsApi {

}

interface EelTunnelsWindowsApi : EelTunnelsApi

/**
 * TODO: DOC
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
suspend fun <T> EelTunnelsApiHelpers.GetConnectionToRemotePort.withConnectionToRemotePort(
  errorHandler: suspend (EelConnectionError) -> T,
  action: suspend CoroutineScope.(Connection) -> T,
): T {
  return try {
    val connectionResult = eelIt()
    closeWithExceptionHandling({ action(connectionResult) }, { connectionResult.close() })
  } catch (e: EelConnectionError) {
    errorHandler(e)
  }
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

fun EelTunnelsApiHelpers.GetConnectionToRemotePort.hostAddress(
  addr: EelTunnelsApi.HostAddress,
): EelTunnelsApiHelpers.GetConnectionToRemotePort =
  hostname(addr.hostname).port(addr.port).protocolPreference(addr.protocolPreference).timeout(addr.timeout)

suspend fun <T> EelTunnelsApi.withConnectionToRemotePort(
  host: String, port: UShort,
  errorHandler: suspend (EelConnectionError) -> T,
  action: suspend CoroutineScope.(Connection) -> T,
): T = getConnectionToRemotePort().hostname(host).port(port).withConnectionToRemotePort(errorHandler, action)

suspend fun <T> EelTunnelsApi.withConnectionToRemotePort(
  remotePort: UShort,
  errorHandler: suspend (EelConnectionError) -> T,
  action: suspend CoroutineScope.(Connection) -> T,
): T = withConnectionToRemotePort("localhost", remotePort, errorHandler, action)


suspend fun <T> EelTunnelsApiHelpers.GetAcceptorForRemotePort.withAcceptorForRemotePort(
  errorHandler: suspend (EelConnectionError) -> T,
  action: suspend CoroutineScope.(EelTunnelsApi.ConnectionAcceptor) -> T,
): T {
  return try {
    val connectionResult = eelIt()
    closeWithExceptionHandling({ action(connectionResult) }, { connectionResult.close() })
  } catch (e: EelConnectionError) {
    errorHandler(e)
  }
}

fun EelTunnelsApiHelpers.GetAcceptorForRemotePort.hostAddress(
  addr: EelTunnelsApi.HostAddress,
): EelTunnelsApiHelpers.GetAcceptorForRemotePort =
  hostname(addr.hostname).port(addr.port).protocolPreference(addr.protocolPreference).timeout(addr.timeout)

/**
 * Represents a common class for all network-related errors appearing during the interaction with IJent or local process
 */
sealed interface EelNetworkError : EelError

/**
 * An error that can happen during the creation of a connection to a remote server
 */
sealed class EelConnectionError(override val message: String) : EelNetworkError, IOException() {

  /**
   * Returned when the remote host cannot create an object of a socket.
   */
  open class SocketAllocationError(override val message: String) : EelConnectionError(message)

  /**
   * Returned when there is a problem with resolve of the hostname.
   */
  open class ResolveFailure(override val message: String) : EelConnectionError(message)

  /**
   * Returned when there was a problem with establishing a connection to a resolved server
   */
  open class ConnectionProblem(override val message: String) : EelConnectionError(message)

  /**
   * Unknown failure during a connection establishment
   */
  open class UnknownFailure(override val message: String) : EelConnectionError(message)
}


private data class HostAddressBuilderImpl(
  override var port: UShort = 0u,
  override var hostname: String = "localhost",
  override var protocolPreference: EelIpPreference = EelIpPreference.USE_SYSTEM_DEFAULT,
  override var timeout: Duration = 10.seconds,
) : EelTunnelsApi.HostAddress.Builder, EelTunnelsApi.HostAddress {
  override fun hostname(hostname: String): EelTunnelsApi.HostAddress.Builder = apply { this.hostname = hostname }

  override fun preferIPv4(): EelTunnelsApi.HostAddress.Builder = apply { this.protocolPreference = EelIpPreference.PREFER_V4 }

  override fun preferIPv6(): EelTunnelsApi.HostAddress.Builder = apply { this.protocolPreference = EelIpPreference.PREFER_V6 }

  override fun preferOSDefault(): EelTunnelsApi.HostAddress.Builder = this.apply { this.protocolPreference = EelIpPreference.USE_SYSTEM_DEFAULT }

  override fun connectionTimeout(timeout: Duration): EelTunnelsApi.HostAddress.Builder = apply { this.timeout = timeout }

  override fun build(): EelTunnelsApi.HostAddress = this.copy()
}
