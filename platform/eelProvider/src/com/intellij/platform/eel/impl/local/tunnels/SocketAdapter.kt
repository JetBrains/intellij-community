// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.impl.local.tunnels

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.platform.eel.EelTunnelsApi
import com.intellij.platform.eel.impl.local.EelLocalApiService
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.ReadableByteChannel
import java.nio.channels.SocketChannel
import java.nio.channels.WritableByteChannel
import kotlin.time.Duration

internal class SocketAdapter(private val channel: SocketChannel) : EelTunnelsApi.Connection {
  private val socket = channel.socket()
  private val scope = ApplicationManager.getApplication().service<EelLocalApiService>().scope(SocketAdapter::class)
  private val clientToSocket = Channel<ByteBuffer>()
  private val socketToClient = Channel<ByteBuffer>()

  init {
    scope.launch {
      try {
        awaitAll(
          async {
            connect(clientToSocket, channel)
          },
          async {
            connect(channel, socketToClient)
          })
      }
      catch (e: IOException) {
        thisLogger().warn(e)
        socket.close()
        scope.cancel()
      }
    }
  }

  override val sendChannel: SendChannel<ByteBuffer> = clientToSocket
  override val receiveChannel: ReceiveChannel<ByteBuffer> = socketToClient

  override suspend fun setSendBufferSize(size: UInt) {
    socket.sendBufferSize = size.toInt()
  }

  override suspend fun setReceiveBufferSize(size: UInt) {
    socket.receiveBufferSize = size.toInt()
  }

  override suspend fun setKeepAlive(keepAlive: Boolean) {
    socket.keepAlive = keepAlive
  }

  override suspend fun setReuseAddr(reuseAddr: Boolean) {
    socket.reuseAddress = reuseAddr
  }

  override suspend fun setLinger(lingerInterval: Duration) {
    val seconds = lingerInterval.inWholeSeconds
    if (seconds == 0L) {
      socket.setSoLinger(false, 0)
    }
    else {
      socket.setSoLinger(true, seconds.toInt())
    }
  }

  override suspend fun setNoDelay(noDelay: Boolean) {
    socket.tcpNoDelay = noDelay
  }

  override suspend fun close() {
    scope.cancel()
    socket.shutdownOutput()
    socket.shutdownInput()
    socket.close()
  }

  private companion object {
    @Throws(IOException::class)
    suspend fun connect(from: ReceiveChannel<ByteBuffer>, to: WritableByteChannel): Unit = withContext(Dispatchers.IO) {
      while (isActive) {
        for (data in from) {
          to.write(data)
        }
      }
    }

    @Throws(IOException::class)
    suspend fun connect(from: ReadableByteChannel, to: SendChannel<ByteBuffer>): Unit = withContext(Dispatchers.IO) {
      val buffer = ByteBuffer.allocate(4096)
      while (isActive) {
        if (from.read(buffer) == -1) {
          break
        }
        buffer.flip()
        to.send(buffer)
        buffer.rewind()
      }
    }
  }
}