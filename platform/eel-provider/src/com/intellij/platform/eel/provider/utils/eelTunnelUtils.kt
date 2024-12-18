// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("IjentTunnelsUtil")

package com.intellij.platform.eel.provider.utils

import com.intellij.openapi.diagnostic.Logger
import com.intellij.platform.eel.EelConnectionError
import com.intellij.platform.eel.EelTunnelsApi
import com.intellij.platform.eel.component1
import com.intellij.platform.eel.component2
import com.intellij.platform.eel.withAcceptorForRemotePort
import com.intellij.platform.eel.withConnectionToRemotePort
import com.intellij.util.io.blockingDispatcher
import com.intellij.util.io.toByteArray
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
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
            tunnels.withConnectionToRemotePort(address, {
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
    tunnels.withAcceptorForRemotePort(address, {
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
private fun CoroutineScope.redirectClientConnectionDataToIJent(connectionId: Int, socket: Socket, channelToIJent: SendChannel<ByteBuffer>) = launch(CoroutineName("Reader for connection $connectionId")) {
  try {
    val inputStream = socket.getInputStream()
    val buffer = ByteArray(4096)
    while (true) {
      try {
        val bytesRead = runInterruptible {
          inputStream.read(buffer, 0, buffer.size)
        }
        LOG.trace("Connection $connectionId; Bytes read: $bytesRead")
        if (bytesRead >= 0) {
          LOG.trace("Sending message to IJent for $connectionId")
          channelToIJent.send(ByteBuffer.wrap(buffer.copyOf(), 0, bytesRead)) // important to make a copy, we have no guarantees when buffer will be processed
        }
        else {
          channelToIJent.close()
          break
        }
      }
      catch (_: SocketTimeoutException) {
        if (channelToIJent.isClosedForSend) {
          this@launch.cancel("Channel is closed normally")
        }
        ensureActive()
      }
    }
  }
  catch (e: ClosedSendChannelException) {
    LOG.warn("Connection $connectionId closed by IJent; $e")
  }
  catch (e: SocketException) {
    if (!socket.isClosed) {
      LOG.warn("Connection $connectionId closed", e)
    }
    channelToIJent.close()
    throw CancellationException("Closed because of a socket exception", e)
  }
}

private fun CoroutineScope.redirectIJentDataToClientConnection(connectionId: Int, socket: Socket, backChannel: ReceiveChannel<ByteBuffer>) = launch(CoroutineName("Writer for connection $connectionId")) {
  try {
    val outputStream = socket.getOutputStream()
    for (data in backChannel) {
      LOG.debug("IJent port forwarding: $connectionId; Received ${data.remaining()} bytes from IJent; sending them back to client connection")
      try {
        runInterruptible {
          outputStream.write(data.toByteArray())
          outputStream.flush()
        }
      }
      catch (_: SocketTimeoutException) {
        ensureActive()
      }
    }
    outputStream.flush()
  }
  catch (e: EelTunnelsApi.RemoteNetworkException) {
    if (e is EelTunnelsApi.RemoteNetworkException.ConnectionReset) {
      socket.close() // causing TCP RST to be sent back
    }
    else {
      LOG.warn("Connection $connectionId closed", Throwable(e))
    }
  }
  catch (e: SocketException) {
    if (!socket.isClosed) {
      LOG.warn("Socket connection $connectionId closed", Throwable(e))
    }
    else {
      LOG.debug("Socket connection $connectionId closed because of cancellation", e)
    }
    backChannel.cancel()
    throw CancellationException("Connection closed", e)
  }
}

fun EelTunnelsApi.ResolvedSocketAddress.asInetAddress(): InetSocketAddress {
  val inetAddress = when (this) {
    is EelTunnelsApi.ResolvedSocketAddress.V4 -> InetAddress.getByAddress(ByteBuffer.allocate(4).putInt(bits.toInt()).toByteArray())
    is EelTunnelsApi.ResolvedSocketAddress.V6 -> InetAddress.getByAddress(ByteBuffer.allocate(16).putLong(higherBits.toLong()).putLong(8, lowerBits.toLong()).array())
  }
  return InetSocketAddress(inetAddress, port.toInt())
}