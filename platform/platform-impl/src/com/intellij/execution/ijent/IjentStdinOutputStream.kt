// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.ijent

import com.intellij.platform.ijent.IjentChildProcess
import com.intellij.platform.util.coroutines.channel.ChannelOutputStream
import kotlinx.coroutines.runBlocking
import java.io.IOException
import java.io.OutputStream
import kotlin.coroutines.CoroutineContext

internal class IjentStdinOutputStream(
  private val coroutineContext: CoroutineContext,
  private val ijentChildProcess: IjentChildProcess,
) : OutputStream() {
  private val delegate = ChannelOutputStream.forArrays(ijentChildProcess.stdin)

  override fun write(b: Int) {
    delegate.write(b)
  }

  override fun write(b: ByteArray, off: Int, len: Int) {
    delegate.write(b, off, len)
  }

  override fun close() {
    delegate.close()
  }

  override fun flush() {
    try {
      @Suppress("SSBasedInspection") (runBlocking(coroutineContext) {
        ijentChildProcess.sendStdinWithConfirmation(byteArrayOf())
      })
    }
    catch (err: IjentChildProcess.SendStdinError) {
      throw IOException("Failed to flush the output stream: ${err.message}", err)
    }
  }
}