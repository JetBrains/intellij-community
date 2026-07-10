// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel

import com.intellij.platform.eel.EelTunnelsApi.Connection
import com.intellij.platform.eel.channels.EelReceiveChannel
import com.intellij.platform.eel.channels.EelSendChannel
import com.intellij.platform.eel.path.EelPath
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.coroutineScope
import org.jetbrains.annotations.ApiStatus
import java.io.IOException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Lets the IDE perform socket operations inside the environment this [EelApi] is bound to, as if it were a local process there —
 * opening outbound connections and accepting inbound ones. The currently supported transports are TCP and Unix-domain sockets.
 *
 * A tunnel runs in one of two directions, as seen from the environment:
 * - **connect** — open an outbound connection from the environment to a host and port resolved there ([getConnectionToRemotePort]);
 * - **listen** — bind a socket inside the environment and accept incoming connections ([listenOnUnixSocket]; accepting on a TCP port
 *   is also supported).
 *
 * Each connection is delivered to the EelApi caller as a pair of [EelSendChannel] / [EelReceiveChannel]. The caller decides what the
 * traffic is for: consume it directly, hand it to IDE-side code, or bridge it to another process such as a debugger. Remote addresses
 * are built with [HostAddress.Builder]; reach this API via [EelApi.tunnels].
 *
 * Note that `localhost` differs across the boundary — the IDE host and a WSL distribution or container each have their own — so a port
 * inside the environment is reached through a tunnel, not a direct socket.
 */
@ApiStatus.Experimental
sealed interface EelTunnelsApi {
  val descriptor: EelDescriptor
  /**
   * Creates a Unix-domain socket in the environment and forwards it: the environment listens for a connection, and when one is
   * accepted the IDE talks to the remote client through a pair of Kotlin channels.
   *
   * Packets sent to and received from the channels may be split and/or concatenated. They may be split only if their size exceeds
   * `RECOMMENDED_MAX_PACKET_SIZE`.
   *
   * The local implementation works on *nix and on modern Windows, which both support Unix-domain sockets natively.
   *
   * On Windows specifically, `AF_UNIX` sockets are supported since Windows 10 (version 1803) and are exposed by the JDK through
   * `StandardProtocolFamily.UNIX`. Like on *nix they are addressed by a filesystem path, which makes them distinct from Windows named
   * pipes (`\\.\pipe\…`) — the traditional Win32 IPC mechanism, which lives in its own namespace rather than on the filesystem and uses
   * a different API. Only Unix-domain sockets are exposed here for now; named-pipe support may come later.
   *
   * A single call accepts only one connection. To accept several, call it in a loop, reusing the allocated path:
   * ```kotlin
   * val (socketPath, tx, rx) = eel.tunnels.listenOnUnixSocket().prefix("ijent-").suffix(".sock").eelIt()
   * println(socketPath) // /tmp/ijent-12345678.sock
   * launch {
   *   handleConnection(tx, rx)
   * }
   * while (true) {
   *   val (_, tx, rx) = eel.tunnels.listenOnUnixSocket(socketPath)
   *   launch {
   *     handleConnection(tx, rx)
   *   }
   * }
   * ```
   */
  @ApiStatus.Experimental
  suspend fun listenOnUnixSocket(fixedPath: EelPath): ListenOnUnixSocketResult

  /**
   * See [listenOnUnixSocket] that accepts [EelPath] parameter for full documentation.
   */
  @ApiStatus.Experimental
  suspend fun listenOnUnixSocket(@GeneratedBuilder temporaryPathOptions: ListenOnUnixSocketTemporaryPathOptions): ListenOnUnixSocketResult

  @ApiStatus.Experimental
  interface ListenOnUnixSocketTemporaryPathOptions {
    val prefix: String get() = ""
    val suffix: String get() = ""
    val parentDirectory: EelPath? get() = null
  }

  @ApiStatus.Experimental
  interface ListenOnUnixSocketResult {
    val unixSocketPath: EelPath
    val tx: EelSendChannel
    val rx: EelReceiveChannel

    operator fun component1(): EelPath = unixSocketPath
    operator fun component2(): EelSendChannel = tx
    operator fun component3(): EelReceiveChannel = rx
  }

  /**
   * **For applied usages, consider using [withConnectionToRemotePort]**.
   *
   * Creates a connection to a TCP socket to a named host specified by the [HostAddress].
   *
   * If an error occurs during establishment of the connection, an [EelConnectionError] will be thrown.
   * Otherwise, a [Connection] object is returned, which means that the connection is ready to use.
   *
   * The connection exists as a pair of channels [Connection.sendChannel] and [Connection.receiveChannel],
   * which allow communicating to a remote server from the IDE side.
   *
   * Packets sent to the channel and received from the channel may be split and/or concatenated.
   * The packets may be split only if their size exceeds `RECOMMENDED_MAX_PACKET_SIZE`.
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
   * To configure a socket before connection use [GetConnectionToRemotePortArgs.configureSocketBeforeConnection]. After that, use [Connection.configureSocket]
   */
  @Throws(EelConnectionError::class)
  @ThrowsChecked(EelConnectionError::class)
  @ApiStatus.Experimental
  suspend fun getConnectionToRemotePort(@GeneratedBuilder args: GetConnectionToRemotePortArgs): Connection

  @ApiStatus.Experimental
  interface GetConnectionToRemotePortArgs : HostAddress {
    @get:ApiStatus.Internal
    val configureSocketBeforeConnection: ConfigurableClientSocket.() -> Unit get() = {}
  }


  @ApiStatus.Experimental
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
  @ApiStatus.Experimental
  interface HostAddress {
    @get:ApiStatus.Experimental
    val port: UShort get() = 0u  // TODO Split into two interfaces

    @get:ApiStatus.Experimental
    val hostname: String get() = "localhost"

    /**
     * @see [Builder.preferIPv4]
     */
    @get:ApiStatus.Experimental
    val protocolPreference: EelIpPreference get() = EelIpPreference.USE_SYSTEM_DEFAULT

    /**
     * @see [Builder.connectionTimeout]
     */
    @get:ApiStatus.Experimental
    val timeout: Duration get() = 10.seconds

    @ApiStatus.Experimental
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
       * If the connection could not be established before [timeout], then an [EelConnectionError] would be thrown
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
      @ApiStatus.Experimental
      fun Builder(port: UShort): Builder = HostAddressBuilderImpl(port)

      /**
       * Creates a builder for address `localhost:0`.
       * This can be useful in remote port forwarding, as it allocates a random port on the remote host side.
       */
      @ApiStatus.Experimental
      fun Builder(): Builder = HostAddressBuilderImpl(0u)
    }
  }


  /**
   * Represents a controller for a TCP connection
   */
  @ApiStatus.Experimental
  interface Connection {

    /**
     * A channel to the server
     */
    @get:ApiStatus.Experimental
    val sendChannel: EelSendChannel

    /**
     * A channel from the server
     */
    @get:ApiStatus.Experimental
    val receiveChannel: EelReceiveChannel

    /**
     * Configure various socket options
     */
    @ApiStatus.Internal
    suspend fun configureSocket(block: suspend ConfigurableClientSocket.() -> Unit)

    /**
     * Closes the connection to the socket.
     */
    @ApiStatus.Experimental
    suspend fun close()
  }

  @ApiStatus.Internal
  sealed class RemoteNetworkException(message: String) : IOException(message) {
    constructor() : this("")

    class ConnectionReset : RemoteNetworkException()
    class UnknownFailure(error: String) : RemoteNetworkException(error)
  }

  /**
   * **For applied usages, please consider [withConnectionToRemotePort]**.
   *
   * Accepts remote connections to a named host specified by the [HostAddress].
   *
   * If an error occurs during creation of the server, an [EelConnectionError] will be thrown.
   * Otherwise, a [ConnectionAcceptor] object is returned, which means that the server was created successfully.
   *
   * Locally, the server exists as a channel of [Connection]s, which allows imitating a server on the IDE side.
   *
   * Packets sent to the channels and received from the channel may be split and/or concatenated.
   * The packets may be split only if their size exceeds `RECOMMENDED_MAX_PACKET_SIZE`.
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
  @ThrowsChecked(EelConnectionError::class)
  @ApiStatus.Internal
  suspend fun getAcceptorForRemotePort(@GeneratedBuilder args: GetAcceptorForRemotePort): ConnectionAcceptor

  @ApiStatus.Experimental
  interface GetAcceptorForRemotePort : HostAddress {
    // TODO Make it look and feel like all other builders.
    val configureServerSocket: ConfigurableSocket.() -> Unit get() = {}
  }

  /**
   * This is a representation of a remote server bound to [boundAddress].
   */
  @ApiStatus.Experimental
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
@ApiStatus.Internal
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
@ApiStatus.Internal
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
@ApiStatus.Internal
operator fun Connection.component1(): EelSendChannel = sendChannel

/**
 * Convenience operator to decompose connection to a pair of channels when needed.
 * @return channel from server
 */
@ApiStatus.Internal
operator fun Connection.component2(): EelReceiveChannel = receiveChannel

/** [EelTunnelsApi] for a POSIX environment. */
@ApiStatus.Experimental
interface EelTunnelsPosixApi : EelTunnelsApi {

}

/** [EelTunnelsApi] for a Windows environment. */
@ApiStatus.Experimental
interface EelTunnelsWindowsApi : EelTunnelsApi

/**
 * Opens a connection to a remote port, runs [action] with it, and closes it afterwards — the scoped counterpart of
 * [EelTunnelsApi.getConnectionToRemotePort].
 *
 * Build the target with [EelTunnelsApi.getConnectionToRemotePort], then call this:
 * ```kotlin
 * eel.tunnels.getConnectionToRemotePort()
 *   .hostname("localhost")
 *   .port(8080u)
 *   .withConnectionToRemotePort(errorHandler = { error -> myErrorReporter.report(error) }) { connection ->
 *     handleConnection(connection.sendChannel, connection.receiveChannel)
 *   }
 * ```
 *
 * If the connection cannot be established, [errorHandler] is invoked with the [EelConnectionError]; otherwise [action] runs and the
 * connection is closed automatically when it finishes (normally or exceptionally).
 *
 * @see EelTunnelsApi.getConnectionToRemotePort for the behavior of the returned [Connection].
 */
@ApiStatus.Experimental
suspend fun <T> EelTunnelsApiHelpers.GetConnectionToRemotePort.withConnectionToRemotePort(
  errorHandler: suspend (EelConnectionError) -> T,
  action: suspend CoroutineScope.(Connection) -> T,
): T {
  return try {
    val connectionResult = eelIt()
    closeWithExceptionHandling({ action(connectionResult) }, { connectionResult.close() })
  }
  catch (e: EelConnectionError) {
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

@ApiStatus.Internal
fun EelTunnelsApiHelpers.GetConnectionToRemotePort.hostAddress(
  addr: EelTunnelsApi.HostAddress,
): EelTunnelsApiHelpers.GetConnectionToRemotePort =
  hostname(addr.hostname).port(addr.port).protocolPreference(addr.protocolPreference).timeout(addr.timeout)

@ApiStatus.Internal
suspend fun <T> EelTunnelsApi.withConnectionToRemotePort(
  host: String, port: UShort,
  errorHandler: suspend (EelConnectionError) -> T,
  action: suspend CoroutineScope.(Connection) -> T,
): T = getConnectionToRemotePort().hostname(host).port(port).withConnectionToRemotePort(errorHandler, action)

@ApiStatus.Internal
suspend fun <T> EelTunnelsApi.withConnectionToRemotePort(
  remotePort: UShort,
  errorHandler: suspend (EelConnectionError) -> T,
  action: suspend CoroutineScope.(Connection) -> T,
): T = withConnectionToRemotePort("localhost", remotePort, errorHandler, action)


@ApiStatus.Internal
suspend fun <T> EelTunnelsApiHelpers.GetAcceptorForRemotePort.withAcceptorForRemotePort(
  errorHandler: suspend (EelConnectionError) -> T,
  action: suspend CoroutineScope.(EelTunnelsApi.ConnectionAcceptor) -> T,
): T {
  return try {
    val connectionResult = eelIt()
    closeWithExceptionHandling({ action(connectionResult) }, { connectionResult.close() })
  }
  catch (e: EelConnectionError) {
    errorHandler(e)
  }
}

@ApiStatus.Internal
fun EelTunnelsApiHelpers.GetAcceptorForRemotePort.hostAddress(
  addr: EelTunnelsApi.HostAddress,
): EelTunnelsApiHelpers.GetAcceptorForRemotePort =
  hostname(addr.hostname).port(addr.port).protocolPreference(addr.protocolPreference).timeout(addr.timeout)

/**
 * Represents a common class for all network-related errors appearing during the interaction with IJent or local process
 */
@ApiStatus.Experimental
sealed interface EelNetworkError : EelError

/**
 * An error that can happen during the creation of a connection to a remote server
 */
@ApiStatus.Experimental
sealed class EelConnectionError : EelNetworkError, IOException {
  constructor(message: String) : super(message)
  constructor(message: String, cause: Throwable) : super(message, cause)

  /**
   * Returned when the remote host cannot create an object of a socket.
   */
  @ApiStatus.Experimental
  @Deprecated("Unlikely to happen, to be merged into `Other`")
  open class SocketAllocationError(message: String) : EelConnectionError(message)

  /**
   * Returned when there is a problem with resolve of the hostname.
   */
  @ApiStatus.Experimental
  open class ResolveFailure(message: String) : EelConnectionError(message)

  /**
   * Returned when there was a problem with establishing a connection to a resolved server
   */
  @ApiStatus.Experimental
  open class ConnectionProblem(message: String) : EelConnectionError(message)

  /**
   * Unknown failure during a connection establishment
   */
  // TODO Rename to `Other`
  @ApiStatus.Experimental
  open class UnknownFailure : EelConnectionError {
    constructor(message: String) : super(message)
    constructor(message: String, cause: Throwable) : super(message, cause)
  }
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
