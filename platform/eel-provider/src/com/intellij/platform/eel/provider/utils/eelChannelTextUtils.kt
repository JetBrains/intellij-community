// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.provider.utils

import com.intellij.platform.eel.ReadResult.*
import com.intellij.platform.eel.channels.EelReceiveChannel
import com.intellij.platform.eel.channels.EelSendChannel
import com.intellij.platform.eel.channels.sendWholeBuffer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.CheckReturnValue
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.Charset

// Tools to send/receive text through eel channels

private val DEFAULT_CHARSET = Charsets.UTF_8

/**
 * Send [text] till the end. Useful to send commands to process
 */
suspend fun EelSendChannel.sendWholeText(text: String, charset: Charset = DEFAULT_CHARSET): Unit = sendWholeBuffer(ByteBuffer.wrap(text.toByteArray(charset)))

/**
 * Reads text from the channel to the end.
 */
suspend fun EelReceiveChannel.readWholeText(bufferSize: Int = DEFAULT_BUFFER_SIZE, charset: Charset = DEFAULT_CHARSET): String = withContext(Dispatchers.IO) {
  val buffer = ByteBuffer.allocate(bufferSize)
  val result = ByteArrayOutputStream()
  while (receive(buffer) != EOF) {
    buffer.flip()
    // Redundant copy isn't optimal but ok for text
    val tmpBuffer = ByteArray(buffer.limit())
    buffer.get(tmpBuffer)
    result.writeBytes(tmpBuffer)
    buffer.clear()
  }
  return@withContext result.toString(charset)
}
