// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(EelDelicateApi::class)

package com.intellij.platform.eel.impl

import com.intellij.platform.eel.EelConnectionError
import com.intellij.platform.eel.EelLowLevelObjectsPool
import com.intellij.platform.eel.EelProxy
import com.intellij.platform.eel.EelTunnelsApi
import com.intellij.platform.eel.ReadResult
import com.intellij.platform.eel.ThrowsChecked
import com.intellij.platform.eel.channels.EelChannelException
import com.intellij.platform.eel.channels.EelDelicateApi
import com.intellij.platform.eel.channels.EelReceiveChannel
import com.intellij.platform.eel.channels.EelReceiveChannelException
import com.intellij.platform.eel.channels.EelSendChannel
import com.intellij.platform.eel.channels.EelSendChannelException
import com.intellij.platform.eel.channels.sendWholeBuffer
import com.intellij.platform.eel.impl.portAccessibleLocally.EelPortAccessibleLocally.Companion.isEelPortAccessibleLocally
import com.intellij.platform.eel.provider.LocalEelDescriptor
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.logging.Logger
import kotlin.time.Duration.Companion.seconds

internal class MyEelProxyImpl : EelProxyImpl {
  @ThrowsChecked(EelConnectionError::class)
  override suspend fun eelProxyImpl(
    acceptorFactory: @ThrowsChecked(EelConnectionError::class) suspend () -> EelTunnelsApi.ConnectionAcceptor,
    connectionFactory: @ThrowsChecked(EelConnectionError::class) suspend () -> EelTunnelsApi.Connection,
    onConnection: ((EelTunnelsApi.Connection) -> Unit)?,
    onConnectionClosed: ((EelTunnelsApi.Connection) -> Unit)?,
    onConnectionError: ((EelConnectionError) -> Unit)?,
    debugLabel: String?,
    acceptorInfo: Pair<EelTunnelsApi, Any?>?,
    connectorInfo: Pair<EelTunnelsApi, Any?>?,
    fakeProxyPossible: Boolean,
  ): EelProxy {
    val fakeListenAddress =
      if (fakeProxyPossible) fakeListenAddress(acceptorInfo, connectorInfo)
      else null

    return if (fakeListenAddress != null) {
      FakeEelProxy(fakeListenAddress)
    }
    else {
      RealEelProxy(
        acceptor = acceptorFactory(),
        remoteConnectionFactory = connectionFactory,
        onConnection = onConnection,
        onConnectionClosed = onConnectionClosed,
        onConnectionError = onConnectionError,
        debugLabel = debugLabel ?: "Eel proxy ${System.nanoTime()}",
      )
    }
  }
}

private class FakeEelProxy(localAddress: EelTunnelsApi.ResolvedSocketAddress) : EelProxy {
  override val acceptor: EelTunnelsApi.ConnectionAcceptor = object : EelTunnelsApi.ConnectionAcceptor {
    override val incomingConnections: ReceiveChannel<EelTunnelsApi.Connection> = Channel<EelTunnelsApi.Connection>().apply { close() }

    override val boundAddress: EelTunnelsApi.ResolvedSocketAddress = localAddress

    override suspend fun close() {
      // Nothing.
    }
  }

  override suspend fun runForever() {
    awaitCancellation()
  }
}

private class RealEelProxy(
  override val acceptor: EelTunnelsApi.ConnectionAcceptor,
  private val remoteConnectionFactory: @ThrowsChecked(exceptionClasses = [EelConnectionError::class]) (suspend () -> EelTunnelsApi.Connection),
  private val onConnection: ((EelTunnelsApi.Connection) -> Unit)?,
  private val onConnectionClosed: ((EelTunnelsApi.Connection) -> Unit)?,
  private val onConnectionError: ((EelConnectionError) -> Unit)?,
  private val debugLabel: String,
) : EelProxy {
  override suspend fun runForever() {
    withContext(CoroutineName(debugLabel) + Dispatchers.IO.limitedParallelism(Int.MAX_VALUE)) {
      try {
        acceptor.incomingConnections.consumeEach { localSocket ->
          launch(CoroutineName("$debugLabel starting handling incoming connection $localSocket")) {
            handleIncomingConnection(
              localSocket = localSocket,
              remoteConnectionFactory = remoteConnectionFactory,
              onConnection = onConnection,
              onConnectionClosed = onConnectionClosed,
              onConnectionError = onConnectionError,
              debugLabel = debugLabel,
            )
          }
        }
      }
      finally {
        finalization {
          acceptor.close()
        }
      }
    }
  }
}

/**
 * Checks if the proxy server actually should be created.
 * For example, it would be impossible to create a proxy server that listens on some port and connects to its local host and its own port.
 *
 * It is deliberately chosen to check different types in runtime to not pollute public API with implementation-detail types.
 */
private suspend fun fakeListenAddress(
  acceptorInfo: Pair<EelTunnelsApi, Any?>?,
  connectorInfo: Pair<EelTunnelsApi, Any?>?,
): EelTunnelsApi.ResolvedSocketAddress? {
  // info is (eelTunnelApi, (TCP, (host, port)))
  // acceptor is an address we listen to
  if (acceptorInfo == null || connectorInfo == null) return null

  val (acceptorKey, acceptorValue) = acceptorInfo.second as? Pair<*, *> ?: return null
  val (connectorKey, connectorValue) = connectorInfo.second as? Pair<*, *> ?: return null

  val (acceptorHost, acceptorPort) = acceptorValue as Pair<*, *>
  val (connectorHost, connectorPort) = connectorValue as Pair<*, *>

  acceptorHost as String
  acceptorPort as UShort
  connectorHost as String
  connectorPort as UShort

  if (acceptorKey != connectorKey) return null
  if (acceptorHost != connectorHost) return null

  require(acceptorKey == "TCP" || acceptorKey == "UDP")
  // connection to host that runs IJ and port acceptorPort is automatically forwarded to eel:connectorPort
  val eelPortAccessibleLocally =
    (
      acceptorInfo.first == connectorInfo.first ||
      (acceptorInfo.first.descriptor == LocalEelDescriptor &&
       isEelPortAccessibleLocally(localPort = acceptorPort, eelPort = connectorPort, onEel = connectorInfo.first.descriptor))
    )
  if (!eelPortAccessibleLocally) return null

  if (acceptorPort == 0.toUShort() || acceptorPort == connectorPort) {
    check(connectorPort > 0u)
    return object : EelTunnelsApi.ResolvedSocketAddress.V4 {
      override val bits: UInt = 127u shl 24 + 1
      override val port: UShort = connectorPort
    }
  }

  return null
}

private suspend fun handleIncomingConnection(
  localSocket: EelTunnelsApi.Connection,
  remoteConnectionFactory: @ThrowsChecked(EelConnectionError::class) suspend () -> EelTunnelsApi.Connection,
  onConnection: ((EelTunnelsApi.Connection) -> Unit)?,
  onConnectionClosed: ((EelTunnelsApi.Connection) -> Unit)?,
  onConnectionError: ((EelConnectionError) -> Unit)?,
  debugLabel: String,
) {
  val connection =
    try {
      remoteConnectionFactory()
    }
    catch (e: EelConnectionError) {
      runCatching { localSocket.close() }.exceptionOrNull()?.let { closeException -> e.addSuppressed(closeException) }
      onConnectionError?.invoke(e)
      return
    }

  onConnection?.invoke(connection)

  try {
    coroutineScope {
      for ((receiveSocket, sendSocket) in listOf(localSocket to connection, connection to localSocket)) {
        val receiveChannel = receiveSocket.receiveChannel
        val sendChannel = sendSocket.sendChannel
        launch(CoroutineName("$debugLabel memory-efficient transfer $receiveSocket ==> $sendSocket")) {
          memoryEfficientTransfer(receiveChannel, sendChannel)
        }
      }
    }
  }
  finally {
    finalization {
      supervisorScope {
        launch { localSocket.close() }
        launch { connection.close() }
        onConnectionClosed?.invoke(connection)
      }
    }
  }
}

private suspend fun memoryEfficientTransfer(receiveChannel: EelReceiveChannel, sendChannel: EelSendChannel) {
  val pool =
    if (receiveChannel.prefersDirectBuffers || sendChannel.prefersDirectBuffers) EelLowLevelObjectsPool.directByteBuffers
    else EelLowLevelObjectsPool.fakeByteBufferPool
  val bufferToReadFrom = pool.borrow()
  try {
    while (true) {
      when (receiveChannel.receive(bufferToReadFrom)) {
        ReadResult.EOF -> {
          break
        }
        ReadResult.NOT_EOF -> {
          bufferToReadFrom.flip()
          sendChannel.sendWholeBuffer(bufferToReadFrom)
          bufferToReadFrom.clear()
        }
      }
    }
  }
  catch (err: EelChannelException) {
    LOG.fine {
      val prefix = when (err) {
        is EelReceiveChannelException -> "Error during receiving from ${err.channel}"
        is EelSendChannelException -> "Error during sending to ${err.channel}"
      }
      "$prefix: ${err.stackTraceToString()}"
    }
  }
  finally {
    pool.returnBack(bufferToReadFrom)
    sendChannel.close(null)
  }
}

private suspend inline fun finalization(crossinline body: suspend CoroutineScope.() -> Unit) {
  withContext(NonCancellable) {
    withTimeoutOrNull(3.seconds) {  // This timeout is taken at random.
      body()
    }
  }
}

private val LOG = Logger.getLogger("com.intellij.platform.eel.EelProxy")