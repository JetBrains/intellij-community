// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.impl.local.tunnels

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.platform.eel.*
import com.intellij.platform.eel.EelTunnelsApi.*
import com.intellij.platform.eel.channels.EelReceiveChannel
import com.intellij.platform.eel.channels.EelSendChannel
import com.intellij.platform.eel.impl.NetworkError
import com.intellij.platform.eel.impl.NetworkOk
import com.intellij.platform.eel.impl.UnknownFailure
import com.intellij.platform.eel.impl.asResolvedSocketAddress
import com.intellij.platform.eel.provider.ResultErrImpl
import com.intellij.platform.eel.provider.ResultOkImpl
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
  override suspend fun listenOnUnixSocket(path: CreateFilePath): ListenOnUnixSocketResult = withContext(Dispatchers.IO) {
    val socketFile = when (path) {
      is CreateFilePath.Fixed -> Path(path.path)
      is CreateFilePath.MkTemp -> with(path) {
        createTempFile(
          directory = if (directory.isEmpty()) null else Path(directory),
          prefix = prefix,
          suffix = suffix,
        ).also {
          // We create a file to generate a path and to make sure we have access, but we can't create a socket if a file exists.
          // This is kinda suboptimal, but people usually do not create sockets too often
          it.deleteExisting()
        }
      }
    }

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
    ApplicationManager.getApplication().service<MyService>().scope.launch(Dispatchers.IO + CoroutineName("UDS for $path")) {
      val client = serverChannel.accept()
      serverChannel.close()
      client.use {
        val fromClient = launch {
          copyWithLoggingAndErrorHandling(client.consumeAsEelChannel(), rx.sink, "fromClient $path") {
            rx.closePipe(it)
          }
        }
        val toClient = launch {
          copyWithLoggingAndErrorHandling(tx.source, client.asEelChannel(), "toClient $path") {
            tx.closePipe(it)
          }
        }
        listOf(fromClient, toClient).joinAll()
        tx.closePipe()
        tx.closePipe()
      }
    }
    ListenOnUnixSocketResult(
      unixSocketPath = socketFile.pathString,
      tx = tx.sink,
      rx = rx.source
    )

  }


  override suspend fun getConnectionToRemotePort(address: HostAddress, configureSocketBeforeConnection: ConfigurableClientSocket.() -> Unit): EelResult<Connection, EelConnectionError> =
    getConnectionToRemotePortImpl(address, configureSocketBeforeConnection)

  override suspend fun getAcceptorForRemotePort(address: HostAddress, configureServerSocket: ConfigurableSocket.() -> Unit): EelResult<ConnectionAcceptor, EelConnectionError> =
    getAcceptorForRemotePortImpl(address, configureServerSocket)

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

private suspend fun getConnectionToRemotePortImpl(address: HostAddress, configureSocket: ConfigurableClientSocket.() -> Unit): EelResult<Connection, EelConnectionError> = withContext(Dispatchers.IO) {
  val socketChannel = address.protocolPreference.protocolFamily?.let {
    SocketChannel.open(it)
  } ?: SocketChannel.open()
  configureSocket(ConfigurableClientSocketImpl(socketChannel.socket()))
  val connKiller = async {
    delay(address.timeout)
    socketChannel.close()
  }
  return@withContext try {
    socketChannel.connect(address.asInetSocketAddress)
    NetworkOk(SocketAdapter(socketChannel))
  }
  catch (e: IOException) {
    NetworkError(UnknownFailure(e.toString()))
  }
  finally {
    connKiller.cancel()
  }
}

private fun getAcceptorForRemotePortImpl(address: HostAddress, configureSocket: ConfigurableSocket.() -> Unit): EelResult<ConnectionAcceptor, EelConnectionError> {
  val channel = try {
    ServerSocketChannel.open().apply {
      bind(address.asInetSocketAddress)
      logger.info("Listening for $localAddress")
    }
  }
  catch (e: IOException) {
    return ResultErrImpl(UnknownFailure(e.localizedMessage))
  }
  configureSocket(ConfigurableServerSocketImpl(channel.socket()))
  return ResultOkImpl(ConnectionAcceptorImpl(channel))
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

private suspend fun copyWithLoggingAndErrorHandling(src: EelReceiveChannel<IOException>, dest: EelSendChannel<IOException>, title: String, onError: (IOException) -> Unit) {
  when (val r = copy(src, dest)) {
    is EelResult.Error -> {
      when (val e = r.error) {
        is CopyResultError.InError -> {
          logger.warn("$title input error", e.inError)
          onError(e.inError)
        }
        is CopyResultError.OutError -> {
          logger.warn("$title output error", e.outError)
          onError(e.outError)
        }
      }
    }
    is EelResult.Ok -> Unit
  }
}