// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.impl.local.tunnels

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.platform.eel.EelConnectionError
import com.intellij.platform.eel.EelIpPreference
import com.intellij.platform.eel.EelTunnelsApi.*
import com.intellij.platform.eel.EelTunnelsPosixApi
import com.intellij.platform.eel.EelTunnelsWindowsApi
import com.intellij.platform.eel.channels.EelReceiveChannel
import com.intellij.platform.eel.channels.EelSendChannel
import com.intellij.platform.eel.impl.asResolvedSocketAddress
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.platform.eel.provider.utils.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.channels.ReceiveChannel
import java.io.IOException
import java.net.*
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.createTempFile
import kotlin.io.path.deleteExisting
import kotlin.io.path.pathString

private val logger = fileLogger()

internal object EelLocalTunnelsApiImpl : EelTunnelsPosixApi, EelTunnelsWindowsApi {
  override suspend fun listenOnUnixSocket(fixedPath: EelPath): ListenOnUnixSocketResult =
    listenOnUnixSocket(Path(fixedPath.toString()))

  override suspend fun listenOnUnixSocket(temporaryPathOptions: ListenOnUnixSocketTemporaryPathOptions): ListenOnUnixSocketResult {
    val socketFile: Path = withContext(Dispatchers.IO) {
      with(temporaryPathOptions) {
        createTempFile(
          directory = parentDirectory?.toString()?.let(::Path),
          prefix = prefix,
          suffix = suffix,
        ).also {
          // We create a file to generate a path and to make sure we have access, but we can't create a socket if a file exists.
          // This is kinda suboptimal, but people usually do not create sockets too often
          it.deleteExisting()
        }
      }
    }
    return listenOnUnixSocket(socketFile)
  }

  private suspend fun listenOnUnixSocket(socketFile: Path): ListenOnUnixSocketResult = withContext(Dispatchers.IO) {
    val tx = EelPipe()
    val rx = EelPipe()
    val serverChannel = ServerSocketChannel.open(StandardProtocolFamily.UNIX)

    // File might already be created.
    // On Windows file can't be used.
    // On Unix it can, but only if it is a socket
    fun bind() {
      serverChannel.bind(UnixDomainSocketAddress.of(socketFile))
    }
    try {
      bind()
    }
    catch (_: BindException) {
      // TODO: Create API to return errors from this function
      deleteFileSilently(socketFile)
      bind()
    }
    ApplicationManager.getApplication().service<MyService>().scope.launch(Dispatchers.IO + CoroutineName("UDS for $socketFile")) {
      val client = serverChannel.accept()
      serverChannel.close()
      client.use {
        val fromClient = launch {
          copyWithLoggingAndErrorHandling(client.consumeAsEelChannel(), rx.sink, "fromClient $socketFile") {
            rx.closePipe(it)
          }
        }
        val toClient = launch {
          copyWithLoggingAndErrorHandling(tx.source, client.asEelChannel(), "toClient $socketFile") {
            tx.closePipe(it)
          }
        }
        listOf(fromClient, toClient).joinAll()
        tx.closePipe()
        tx.closePipe()
      }
    }
    object : ListenOnUnixSocketResult {
      override val unixSocketPath = EelPath.parse(socketFile.pathString, LocalEelDescriptor)
      override val tx = tx.sink
      override val rx = rx.source
    }

  }


  override suspend fun getConnectionToRemotePort(args: GetConnectionToRemotePortArgs): Connection =
    getConnectionToRemotePortImpl(args)

  override suspend fun getAcceptorForRemotePort(args: GetAcceptorForRemotePort): ConnectionAcceptor =
    getAcceptorForRemotePortImpl(args)
}

private suspend fun deleteFileSilently(file: Path) {
  withContext(Dispatchers.IO) {
    try {
      Files.delete(file)
    }
    catch (_: IOException) {
    }
  }
}

private suspend fun getConnectionToRemotePortImpl(args: GetConnectionToRemotePortArgs): Connection = withContext(Dispatchers.IO) {
  val socketChannel = args.protocolPreference.protocolFamily?.let {
    SocketChannel.open(it)
  } ?: SocketChannel.open()
  args.configureSocketBeforeConnection(ConfigurableClientSocketImpl(socketChannel.socket()))
  val connKiller = async {
    delay(args.timeout)
    socketChannel.close()
  }
  return@withContext try {
    socketChannel.connect(args.asInetSocketAddress)
    SocketAdapter(socketChannel)
  }
  catch (e: IOException) {
    throw EelConnectionError.UnknownFailure(e.toString())
  }
  finally {
    connKiller.cancel()
  }
}

private fun getAcceptorForRemotePortImpl(args: GetAcceptorForRemotePort): ConnectionAcceptor {
  val channel = try {
    ServerSocketChannel.open().apply {
      bind(args.asInetSocketAddress)
      logger.info("Listening for $localAddress")
    }
  }
  catch (e: IOException) {
    throw EelConnectionError.UnknownFailure(e.localizedMessage)
  }
  args.configureServerSocket(ConfigurableServerSocketImpl(channel.socket()))
  return ConnectionAcceptorImpl(channel)
}

private val EelIpPreference.protocolFamily: ProtocolFamily?
  get() = when (this) {
    EelIpPreference.PREFER_V4 -> StandardProtocolFamily.INET
    EelIpPreference.PREFER_V6 -> StandardProtocolFamily.INET6
    EelIpPreference.USE_SYSTEM_DEFAULT -> null
  }

private val HostAddress.asInetSocketAddress: InetSocketAddress get() = InetSocketAddress(hostname, port.toInt())

private class ConnectionAcceptorImpl(private val boundServerSocket: ServerSocketChannel) : ConnectionAcceptor {
  private val listenSocket: Job

  init {
    assert(boundServerSocket.isOpen)
    listenSocket = ApplicationManager.getApplication().service<MyService>().scope.launch(Dispatchers.IO + CoroutineName("eel socket accept")) {
      try {
        val channel = boundServerSocket.accept()
        logger.info("Connection from ${channel.socket().remoteSocketAddress}")
        try {
          _incomingConnections.send(SocketAdapter(channel))
        }
        catch (_: ClosedSendChannelException) {
          channel.close()
        }
      }
      catch (e: IOException) {
        closeImpl(e)
      }
    }
  }

  private val _incomingConnections = Channel<Connection>()
  override val incomingConnections: ReceiveChannel<Connection> = _incomingConnections
  override val boundAddress: ResolvedSocketAddress = boundServerSocket.localAddress.asResolvedSocketAddress

  override suspend fun close() {
    closeImpl()
  }

  private fun closeImpl(err: Throwable? = null) {
    fileLogger().warn("Closing with exception", err)
    listenSocket.cancel()
    boundServerSocket.close()
    _incomingConnections.close(err)
    _incomingConnections.cancel()
  }
}

@Service
private class MyService(val scope: CoroutineScope)

private suspend fun copyWithLoggingAndErrorHandling(src: EelReceiveChannel, dest: EelSendChannel, title: String, onError: (IOException) -> Unit) {
  try {
    copy(src, dest)
  } catch (e: CopyError) {
    when (e) {
      is CopyError.InError -> {
        logger.warn("$title input error", e.cause)
        onError(e.cause as IOException)
      }
      is CopyError.OutError -> {
        logger.warn("$title output error", e.cause)
        onError(e.cause as IOException)
      }
    }
  }
}
