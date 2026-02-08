// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("IjentTunnelsUtil")
@file:OptIn(IntellijInternalApi::class)

package com.intellij.platform.eel.provider.utils

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.platform.eel.EelConnectionError
import com.intellij.platform.eel.EelTunnelsApi
import com.intellij.platform.eel.ThrowsChecked
import com.intellij.platform.eel.channels.EelDelicateApi
import com.intellij.platform.eel.eelProxy
import com.intellij.platform.eel.getAcceptorForRemotePort
import com.intellij.platform.eel.provider.localEel
import com.intellij.util.io.toByteArray
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.jetbrains.annotations.ApiStatus
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.ByteBuffer

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
@OptIn(DelicateCoroutinesApi::class, EelDelicateApi::class)
@ApiStatus.Experimental
@Deprecated("Use com.intellij.platform.eel.eelProxy")
fun CoroutineScope.forwardLocalPort(tunnels: EelTunnelsApi, localPort: Int, address: EelTunnelsApi.HostAddress) {
  var connectionCounter = 0
  val eelProxyBuilder = eelProxy()
    .acceptOnTcpPort(localEel.tunnels, port = localPort.toUShort())
    .connectToTcpPort(tunnels, host = address.hostname, port = address.port)
    .onConnection {
      connectionCounter++
    }
    .onConnectionError {
      if (it is EelConnectionError.ConnectionProblem) {
        LOG.debug("Failed to establish connection $connectionCounter ($localPort - $address: $it); closing socket")
      }
      else {
        LOG.error("Failed to connect to remote port $localPort - $address: $it")
      }
      connectionCounter++
    }

  val proxy =
    try {
      @Suppress("RAW_RUN_BLOCKING")
      runBlocking { eelProxyBuilder.eelIt() }
    }
    catch (e: EelConnectionError) {
      LOG.error("Failed to start a server on $address (was forwarding $localPort): $e")
      return
    }

  launch {
    try {
      proxy.runForever()
    }
    finally {
      LOG.info("Local server on $localPort (was tunneling to $address) is terminated")
    }
  }
}

/**
 * Finds an available port on the EEL machine by creating a temporary server socket.
 *
 * ## Why @EelDelicateApi?
 *
 * Time-of-Check to Time-of-Use (TOCTOU) race condition - same issue as
 * `com.intellij.util.net.NetUtils#findAvailableSocketPort`:
 *
 * 1. Opens socket with port 0, gets available port
 * 2. Closes socket immediately
 * 3. Returns port number
 * 4. Another process can grab the port before you bind to it
 *
 * The returned port is NOT guaranteed to be available when you use it.
 */
@EelDelicateApi
@ThrowsChecked(EelConnectionError::class)
@ApiStatus.Experimental
suspend fun EelTunnelsApi.findAvailablePort(): Int {
  val acceptor = getAcceptorForRemotePort().eelIt()
  acceptor.close()
  return acceptor.boundAddress.port.toInt()
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
@ApiStatus.Experimental
@Deprecated("Use com.intellij.platform.eel.eelProxy")
fun CoroutineScope.forwardLocalServer(tunnels: EelTunnelsApi, localPort: Int, address: EelTunnelsApi.HostAddress): Deferred<EelTunnelsApi.ResolvedSocketAddress> {
  return async {
    try {
      val proxy =
        eelProxy()
          .acceptOnTcpPort(tunnels, host = address.hostname, port = address.port)
          .connectToTcpPort(localEel.tunnels, port = localPort.toUShort())
          .onConnectionClosed { currentConnection ->
            LOG.debug("Stopped forwarding remote connection $currentConnection to local server")
          }
          .eelIt()

      this@forwardLocalServer.launch {
        try {
          proxy.runForever()
        }
        finally {
          LOG.info("Remote server on $address (was tunneling local server on $address) is terminated")
        }
      }

      proxy.acceptor.boundAddress
    }
    catch (e: EelConnectionError) {
      LOG.error("Failed to start a server on $address (was forwarding $localPort): $e")
      this@async.cancel()
      ensureActive()
      error("unreachable")
    }
  }
}

@ApiStatus.Experimental
fun EelTunnelsApi.ResolvedSocketAddress.asInetAddress(): InetSocketAddress {
  val inetAddress = when (this) {
    is EelTunnelsApi.ResolvedSocketAddress.V4 -> InetAddress.getByAddress(ByteBuffer.allocate(4).putInt(bits.toInt()).toByteArray())
    is EelTunnelsApi.ResolvedSocketAddress.V6 -> InetAddress.getByAddress(ByteBuffer.allocate(16).putLong(higherBits.toLong()).putLong(8, lowerBits.toLong()).array())
  }
  return InetSocketAddress(inetAddress, port.toInt())
}