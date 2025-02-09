// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.impl.local.tunnels

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.platform.eel.EelConnectionError
import com.intellij.platform.eel.EelIpPreference
import com.intellij.platform.eel.EelResult
import com.intellij.platform.eel.EelTunnelsApi
import com.intellij.platform.eel.EelTunnelsApi.ConnectionAcceptor
import com.intellij.platform.eel.impl.NetworkError
import com.intellij.platform.eel.impl.NetworkOk
import com.intellij.platform.eel.impl.UnknownFailure
import com.intellij.platform.eel.impl.asResolvedSocketAddress
import com.intellij.platform.eel.provider.ResultErrImpl
import com.intellij.platform.eel.provider.ResultOkImpl
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.channels.ReceiveChannel
import java.io.IOException
import java.net.InetSocketAddress
import java.net.ProtocolFamily
import java.net.StandardProtocolFamily
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel

private val logger = fileLogger()

internal suspend fun getConnectionToRemotePortImpl(address: EelTunnelsApi.HostAddress): EelResult<EelTunnelsApi.Connection, EelConnectionError> = withContext(Dispatchers.IO) {
  val socketChannel = address.protocolPreference.protocolFamily?.let {
    SocketChannel.open(it)
  } ?: SocketChannel.open()
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

internal fun getAcceptorForRemotePortImpl(address: EelTunnelsApi.HostAddress): EelResult<ConnectionAcceptor, EelConnectionError> {
  val channel = try {
    ServerSocketChannel.open().apply {
      bind(address.asInetSocketAddress)
      logger.info("Listening for $localAddress")
    }
  }
  catch (e: IOException) {
    return ResultErrImpl(UnknownFailure(e.localizedMessage))
  }
  return ResultOkImpl(ConnectionAcceptorImpl(channel))
}

private val EelIpPreference.protocolFamily: ProtocolFamily?
  get() = when (this) {
    EelIpPreference.PREFER_V4 -> StandardProtocolFamily.INET
    EelIpPreference.PREFER_V6 -> StandardProtocolFamily.INET6
    EelIpPreference.USE_SYSTEM_DEFAULT -> null
  }

private val EelTunnelsApi.HostAddress.asInetSocketAddress: InetSocketAddress get() = InetSocketAddress(hostname, port.toInt())

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

  private val _incomingConnections = Channel<EelTunnelsApi.Connection>()
  override val incomingConnections: ReceiveChannel<EelTunnelsApi.Connection> = _incomingConnections
  override val boundAddress: EelTunnelsApi.ResolvedSocketAddress = boundServerSocket.localAddress.asResolvedSocketAddress

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