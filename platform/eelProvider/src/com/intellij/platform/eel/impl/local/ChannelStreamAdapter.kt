// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.impl.local

import com.intellij.openapi.diagnostic.Logger
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

private val LOG = Logger.getInstance(ChannelWrapper::class.java)

/**
 * Wraps [Channel] to [close] [Closeable] stream along with the [channel]
 */
internal class ChannelWrapper(private val stream: Closeable, private val channel: Channel<ByteArray> = Channel()) : Channel<ByteArray> by channel {
  override fun close(cause: Throwable?): Boolean {
    try {
      stream.close()
    }
    catch (e: IOException) {
      LOG.info(e)
    }
    return this@ChannelWrapper.channel.close(cause)
  }
}

/**
 * Use inheritors
 */
internal sealed class StreamWrapper(private val scope: CoroutineScope, stream: Closeable) {
  protected val channel = ChannelWrapper(stream)

  protected fun connect(): Channel<ByteArray> {
    scope.launch {
      connectAsync()
    }.invokeOnCompletion {
      this@StreamWrapper.channel.close(it)
    }
    return channel
  }

  /**
   * Infinite fun to connect stream to the channel
   */
  protected abstract suspend fun connectAsync()


  /**
   * Connects [InputStream] with [ReceiveChannel]: use [connectChannel]
   */
  class InputStreamWrapper(scope: CoroutineScope, private val inputStream: InputStream) : StreamWrapper(scope, inputStream) {
    fun connectChannel(): ReceiveChannel<ByteArray> = connect()


    private val BUF_SIZE = 4096
    override suspend fun connectAsync() = withContext(Dispatchers.IO) {
      // If we used ByteBuffer instead of ByteArray we wouldn't need to copy buffer on each call.
      // TODO: Migrate to ByteBuffer
      val buffer = ByteArray(BUF_SIZE)
      while (isActive) {
        val bytesRead = try {
          inputStream.read(buffer)
        }
        catch (e: IOException) {
          LOG.info(e)
          break
        }
        if (bytesRead == -1) {
          break
        }
        val bytesToSend = ByteArray(bytesRead)
        withContext(Dispatchers.Default) { System.arraycopy(buffer, 0, bytesToSend, 0, bytesRead) }
        channel.send(bytesToSend)
      }
    }
  }

  /**
   * Connects [OutputStream] with [SendChannel]: use [connectChannel]
   */
  internal class OutputStreamWrapper(scope: CoroutineScope, private val outputStream: OutputStream) : StreamWrapper(scope, outputStream) {
    fun connectChannel(): SendChannel<ByteArray> = connect()

    override suspend fun connectAsync() {
      for (bytes in channel) {
        try {
          outputStream.write(bytes)
          outputStream.flush()
        }
        catch (e: IOException) {
          LOG.info(e)
          return
        }
      }
    }
  }
}
