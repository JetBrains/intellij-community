// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(EelDelicateApi::class)

package com.intellij.platform.eel

import com.intellij.platform.eel.channels.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.consumeEach
import org.jetbrains.annotations.ApiStatus
import java.io.IOException
import java.net.SocketTimeoutException
import java.nio.ByteBuffer
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

// TODO Maybe move to eel.impl?
@ThrowsChecked(EelConnectionError::class)
internal suspend fun eelProxyImpl(
  acceptorFactory: @ThrowsChecked(EelConnectionError::class) suspend () -> EelTunnelsApi.ConnectionAcceptor,
  connectionFactory: @ThrowsChecked(EelConnectionError::class) suspend () -> EelTunnelsApi.Connection,
  transferSpeed: EelTunnelsApiRunProxyOpts.TransferSpeed,
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
      transferSpeed = transferSpeed,
      onConnection = onConnection,
      onConnectionClosed = onConnectionClosed,
      onConnectionError = onConnectionError,
      debugLabel = debugLabel ?: "Eel proxy ${System.nanoTime()}",
    )
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
  private val transferSpeed: EelTunnelsApiRunProxyOpts.TransferSpeed,
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
              transferSpeed = transferSpeed,
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
private fun fakeListenAddress(
  acceptorInfo: Pair<EelTunnelsApi, Any?>?,
  connectorInfo: Pair<EelTunnelsApi, Any?>?,
): EelTunnelsApi.ResolvedSocketAddress? {
  if (acceptorInfo == null || connectorInfo == null) return null
  if (acceptorInfo.first != connectorInfo.first) return null

  val (acceptorKey, acceptorValue) = acceptorInfo.second as? Pair<*, *> ?: return null
  val (connectorKey, connectorValue) = connectorInfo.second as? Pair<*, *> ?: return null

  if (acceptorKey != connectorKey) return null

  require(acceptorKey == "TCP")
  val (acceptorHost, acceptorPort) = acceptorValue as Pair<*, *>
  val (connectorHost, connectorPort) = connectorValue as Pair<*, *>

  acceptorHost as String
  acceptorPort as UShort
  connectorHost as String
  connectorPort as UShort

  if (acceptorHost != connectorHost) return null

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
  transferSpeed: EelTunnelsApiRunProxyOpts.TransferSpeed,
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
      onConnectionError?.invoke(e)
      return
    }

  onConnection?.invoke(connection)

  try {
    coroutineScope {
      for ((receiveSocket, sendSocket) in listOf(localSocket to connection, connection to localSocket)) {
        val receiveChannel = receiveSocket.receiveChannel
        val sendChannel = sendSocket.sendChannel
        when (transferSpeed) {
          EelTunnelsApiRunProxyOpts.TransferSpeed.MEMORY_EFFICIENT -> {
            launch(CoroutineName("$debugLabel memory-efficient transfer $receiveSocket ==> $sendSocket")) {
              memoryEfficientTransfer(receiveChannel, sendChannel)
            }
          }
          EelTunnelsApiRunProxyOpts.TransferSpeed.READ_AHEAD -> {
            val debugLabel = "$debugLabel read-ahead transfer $receiveSocket ==> $sendSocket"
            launch(CoroutineName(debugLabel)) {
              readAheadTransfer(receiveChannel, sendChannel, debugLabel)
            }
          }

          EelTunnelsApiRunProxyOpts.TransferSpeed.TMP_COPY_MODE -> {
            val debugLabel = "$debugLabel tmp-copy transfer $receiveSocket ==> $sendSocket"
            launch(CoroutineName(debugLabel)) {
              copy(receiveChannel, sendChannel,
                   onReadError = {
                     if (it is SocketTimeoutException && sendChannel.isClosed) {
                       OnError.EXIT
                     }
                     else {
                       OnError.RETRY
                     }
                   })
              sendChannel.close(null)
            }
          }
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

private fun CoroutineScope.readAheadTransfer(receiveChannel: EelReceiveChannel, sendChannel: EelSendChannel, debugLabel: String) {
  val readAheadNumber = 2
  val transferChannel = Channel<ByteBuffer>(readAheadNumber)

  val pool =
    if (receiveChannel.prefersDirectBuffers || sendChannel.prefersDirectBuffers) EelLowLevelObjectsPool.directByteBuffers
    else EelLowLevelObjectsPool.fakeByteBufferPool

  launch(CoroutineName("$debugLabel: reading from $receiveChannel")) {
    receiveIntoQueue(pool, receiveChannel, transferChannel, debugLabel)
  }

  launch(CoroutineName("$debugLabel: writing to $sendChannel")) {
    sendFromQueue(pool, transferChannel, sendChannel)
  }
}

private suspend fun receiveIntoQueue(
  pool: EelLowLevelObjectsPool<ByteBuffer>,
  receiveChannel: EelReceiveChannel,
  sendChannel: SendChannel<ByteBuffer>,
  debugLabel: String,
) {
  var failed = false
  try {
    while (true) {
      val buffer = pool.borrow()
      when (receiveChannel.receive(buffer)) {
        ReadResult.EOF -> {
          break
        }
        ReadResult.NOT_EOF -> {
          buffer.flip()
          sendChannel.send(buffer)
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
    failed = true
  }
  finally {
    sendChannel.close()
    finalization {
      if (!failed) {
        launch(CoroutineName("$debugLabel: finalization")) { receiveChannel.closeForReceive() }
      }
    }
  }
}

private suspend fun sendFromQueue(
  pool: EelLowLevelObjectsPool<ByteBuffer>,
  receiveChannel: ReceiveChannel<ByteBuffer>,
  sendChannel: EelSendChannel,
) {
  var failed = false
  try {
    for (buffer in receiveChannel) {
      try {
        sendChannel.sendWholeBuffer(buffer)
      }
      finally {
        pool.returnBack(buffer)
      }
    }
  }
  catch (err: EelSendChannelException) {
    LOG.fine { "Error during sending to ${err.channel}: ${err.stackTraceToString()}" }
    failed = true
  }
  catch (err: Throwable) {
    err.printStackTrace()
    throw err
  }
  finally {
    finalization {
      if (!failed) {
        sendChannel.close(null)
      }
    }
  }
}

private suspend inline fun finalization(crossinline body: suspend CoroutineScope.() -> Unit) {
  withContext(NonCancellable) {
    withTimeoutOrNull(3.seconds) {  // This timeout is taken at random.
      body()
    }
  }
}

private val LOG = java.util.logging.Logger.getLogger("com.intellij.platform.eel.EelProxy")

/**
 * Result of [copy]
 */
@ApiStatus.Internal
private sealed class CopyError(override val cause: Throwable) : IOException() {
  class InError(override val cause: Throwable) : CopyError(cause)
  class OutError(override val cause: Throwable) : CopyError(cause)
}

@ApiStatus.Internal
private enum class OnError {
  /**
   * Pretend to error happened, and try again after some time (simple backoff algorithm is used)
   */
  RETRY,

  /**
   * Return with error
   */
  EXIT
}

/**
 * TODO It is a copy-paste. After benchmarking, the original should be improved, and the copy-paste should be deleted.
 */
@Throws(CopyError::class)
@ApiStatus.Internal
private suspend fun copy(
  src: EelReceiveChannel,
  dst: EelSendChannel,
  bufferSize: Int = DEFAULT_BUFFER_SIZE,
  // CPU-bound, but due to the mokk error can't be used in tests. Used default in prod.
  dispatcher: CoroutineDispatcher = Dispatchers.Default,
  onReadError: suspend (IOException) -> OnError = { OnError.EXIT },
  onWriteError: suspend (IOException) -> OnError = { OnError.EXIT },
): Unit = withContext(dispatcher) {
  assert(bufferSize > 0)
  var sendBackoff = backoff()
  var receiveBackoff = backoff()

  val pool =
    if (src.prefersDirectBuffers || dst.prefersDirectBuffers) EelLowLevelObjectsPool.directByteBuffers
    else EelLowLevelObjectsPool.fakeByteBufferPool
  val buffer = pool.borrow()
  try {
    while (true) {
      buffer.clear()
      // read data
      try {
        val r = src.receive(buffer)
        if (r == ReadResult.EOF) {
          break
        }
        else {
          receiveBackoff = backoff()
        }
      }
      catch (error: IOException) {
        when (onReadError(error)) {
          OnError.RETRY -> {
            delay(receiveBackoff.next())
            continue
          }
          OnError.EXIT -> throw CopyError.InError(error)
        }
      }
      buffer.flip()
      do {
        // write data
        try {
          dst.sendWholeBuffer(buffer)
          sendBackoff = backoff()
        }
        catch (error: IOException) {
          when (onWriteError(error)) {
            OnError.RETRY -> {
              delay(sendBackoff.next())
              continue
            }
            OnError.EXIT -> throw CopyError.OutError(error)
          }
        }
      }
      while (buffer.hasRemaining())
      ensureActive()
    }
  }
  finally {
    pool.returnBack(buffer)
  }
}


// Slowly increase timeout
private fun backoff(): Iterator<Duration> = ((200..1000 step 200).asSequence() + generateSequence(1000) { 1000 }).map { it.milliseconds }.iterator()