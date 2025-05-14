// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("IjentTunnelsUtil")
@file:OptIn(IntellijInternalApi::class)

package com.intellij.platform.eel.provider.utils

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.platform.eel.*
import com.intellij.platform.eel.channels.EelReceiveChannel
import com.intellij.platform.eel.channels.EelSendChannel
import com.intellij.util.io.blockingDispatcher
import com.intellij.util.io.toByteArray
import kotlinx.coroutines.*
import java.io.IOException
import java.net.*
import java.nio.ByteBuffer
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit

private val LOG: Logger = Logger.getInstance(EelTunnelsApi::class.java)

/**
 * A tunnel to a remote server (a.k.a. local port forwarding).
 *
 * This function spawns a server on the IDE-host side, and starts accepting connections to "localhost:[localPort]".
 * Each connection to this hostname results in a connection to a server located at [address] on the remote side.
 * The data is transferred from the local connections to remote ones and back, thus establishing a tunnel.
 *
 * The lifetime of port forwarding is bound to the accepting [CoroutineScope]. The receiving [CoroutineScope] cannot complete normally when
 * it runs port forwarding.
 *
 * This function returns when the server starts accepting connections.
 */
@OptIn(DelicateCoroutinesApi::class)
fun CoroutineScope.forwardLocalPort(tunnels: EelTunnelsApi, localPort: Int, address: EelTunnelsApi.HostAddress) {
  val serverSocket = ServerSocket()
  serverSocket.bind(InetSocketAddress("localhost", localPort), 1024)
  serverSocket.soTimeout = 2.seconds.toInt(DurationUnit.MILLISECONDS)
  this.coroutineContext.job.invokeOnCompletion {
    LOG.debug("Closing connection to server socket")
    serverSocket.close()
  }
  LOG.info("Accepting a connection within IDE client on port $localPort; Conections go to remote address $address")
  var connectionCounter = 0
  // DO NOT use Dispatchers.IO here. Sockets live long enough to cause starvation of the IO dispatcher.
  launch(blockingDispatcher.plus(CoroutineName("Local port forwarding server"))) {
    while (true) {
      try {
        LOG.debug("Accepting a coroutine on server socket")
        val socket = runInterruptible {
          serverSocket.accept()
        }
        socket.soTimeout = 2.seconds.toInt(DurationUnit.MILLISECONDS)
        val currentConnection = connectionCounter
        connectionCounter++
        launch {
          socket.use {
            tunnels.getConnectionToRemotePort().hostAddress(address).withConnectionToRemotePort({
                                                                                                  if (it is EelConnectionError.ConnectionProblem) {
                                                                                                    LOG.debug("Failed to establish connection $connectionCounter ($localPort - $address: $it); closing socket")
                                                                                                  }
                                                                                                  else {
                                                                                                    LOG.error("Failed to connect to remote port $localPort - $address: $it")
                                                                                                  }
                                                                                                }) { (channelTo, channelFrom) ->
              redirectClientConnectionDataToIJent(currentConnection, socket, channelTo)
              redirectIJentDataToClientConnection(currentConnection, socket, channelFrom)
            }
          }
        }
      }
      catch (e: SocketTimeoutException) {
        ensureActive() // just checking if it is time to abort the port forwarding
      }
    }
  }.invokeOnCompletion {
    LOG.info("Local server on $localPort (was tunneling to $address) is terminated")
  }
}

/**
 * A reverse tunnel to a local server (a.k.a. remote port forwarding).
 *
 * This function spawns a server on the remote side, and starts accepting connections to [address].
 * Each connection to this hostname results in a connection to a server located at "localhost:[localPort]" on the local side.
 * The data is transferred from the remote connections to local ones and back, thus establishing tunnels.
 *
 * The lifetime of port forwarding is bound to the accepting [CoroutineScope]. The receiving [CoroutineScope] cannot complete normally when
 * it runs port forwarding.
 *
 * This function returns when the remote server starts accepting connections.
 * @return An address which remote server listens to. It is useful to get the actual port if the port of [address] was 0
 */
@OptIn(DelicateCoroutinesApi::class)
fun CoroutineScope.forwardLocalServer(tunnels: EelTunnelsApi, localPort: Int, address: EelTunnelsApi.HostAddress): Deferred<EelTunnelsApi.ResolvedSocketAddress> {
  // todo: do not forward anything if it is local eel
  val remoteAddress = CompletableDeferred<EelTunnelsApi.ResolvedSocketAddress>()
  launch(blockingDispatcher.plus(CoroutineName("Local server on port $localPort (tunneling to $address)"))) {
    tunnels.getAcceptorForRemotePort().hostAddress(address).withAcceptorForRemotePort({
                                                                                        LOG.error("Failed to start a server on $address (was forwarding $localPort): $it")
                                                                                        remoteAddress.cancel()
                                                                                      }) { acceptor ->
      remoteAddress.complete(acceptor.boundAddress)
      var connections = 0
      for ((sendChannel, receiveChannel) in acceptor.incomingConnections) {
        val currentConnection = connections++
        launch {
          Socket().use { socket ->
            coroutineScope {
              socket.soTimeout = 2.seconds.toInt(DurationUnit.MILLISECONDS)
              socket.connect(InetSocketAddress("localhost", localPort))
              redirectClientConnectionDataToIJent(currentConnection, socket, sendChannel)
              redirectIJentDataToClientConnection(currentConnection, socket, receiveChannel)
            }
            LOG.debug("Stopped forwarding remote connection $currentConnection to local server")
          }
        }
      }
    }
  }.invokeOnCompletion {
    LOG.info("Remote server on $address (was tunneling local server on $address) is terminated")
  }
  return remoteAddress
}

@OptIn(DelicateCoroutinesApi::class)
private fun CoroutineScope.redirectClientConnectionDataToIJent(connectionId: Int, socket: Socket, channelToIJent: EelSendChannel) = launch(CoroutineName("Reader for connection $connectionId")) {

  try {
    copy(
      socket.consumeAsEelChannel(), channelToIJent,
      onReadError = {
        if (it is SocketTimeoutException && channelToIJent.closed) {
          this@launch.cancel("Channel is closed normally")
          OnError.EXIT
        }
        else {
          OnError.RETRY
        }
      },
    )
  }
  catch (error: IOException) {
    when (val error = error) {
      is CopyError.InError -> {
        if (!socket.isClosed) {
          LOG.warn("Connection $connectionId closed", error.cause)
          throw CancellationException("Closed because of a socket exception", error.cause)
        }
      }
      is CopyError.OutError -> {
        LOG.warn("Connection $connectionId closed by IJent; ${error.cause}")
      }
    }
  }
  channelToIJent.close()
}

private fun CoroutineScope.redirectIJentDataToClientConnection(connectionId: Int, socket: Socket, backChannel: EelReceiveChannel) = launch(CoroutineName("Writer for connection $connectionId")) {

  try {
    copy(backChannel, socket.asEelChannel(), onReadError = {
      if (it is SocketTimeoutException) OnError.RETRY else OnError.EXIT
    })
  }
  catch (error: IOException) {
    when (val error = error) {
      is CopyError.InError -> {
        LOG.warn("Error reading from channel", error.cause)
      }
      is CopyError.OutError -> {
        val e = error.cause
        if (!socket.isClosed) {
          LOG.warn("Socket connection $connectionId closed", e)
        }
        else {
          LOG.debug("Socket connection $connectionId closed because of cancellation", e)
        }
      }
    }
  }
  backChannel.close()
}

fun EelTunnelsApi.ResolvedSocketAddress.asInetAddress(): InetSocketAddress {
  val inetAddress = when (this) {
    is EelTunnelsApi.ResolvedSocketAddress.V4 -> InetAddress.getByAddress(ByteBuffer.allocate(4).putInt(bits.toInt()).toByteArray())
    is EelTunnelsApi.ResolvedSocketAddress.V6 -> InetAddress.getByAddress(ByteBuffer.allocate(16).putLong(higherBits.toLong()).putLong(8, lowerBits.toLong()).array())
  }
  return InetSocketAddress(inetAddress, port.toInt())
}