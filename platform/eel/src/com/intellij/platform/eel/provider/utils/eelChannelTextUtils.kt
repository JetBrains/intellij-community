// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.provider.utils

import com.intellij.platform.eel.ThrowsChecked
import com.intellij.platform.eel.channels.EelReceiveChannel
import com.intellij.platform.eel.channels.EelReceiveChannelException
import com.intellij.platform.eel.channels.EelSendChannel
import com.intellij.platform.eel.channels.EelSendChannelException
import com.intellij.platform.eel.channels.sendWholeBuffer
import org.jetbrains.annotations.ApiStatus
import java.nio.ByteBuffer
import java.nio.charset.Charset

// Tools to send/receive text through eel channels

private val DEFAULT_CHARSET = Charsets.UTF_8

/**
 * Send [text] till the end. Useful to send commands to process
 */
@ThrowsChecked(EelSendChannelException::class)
@ApiStatus.Internal
suspend fun EelSendChannel.sendWholeText(text: String, charset: Charset = DEFAULT_CHARSET): Unit = sendWholeBuffer(ByteBuffer.wrap(text.toByteArray(charset)))

/**
 * Reads text from the channel to the end, see [readAllBytes]
 */
@ThrowsChecked(EelReceiveChannelException::class)
@ApiStatus.Internal
suspend fun EelReceiveChannel.readWholeText(bufferSize: Int = DEFAULT_BUFFER_SIZE, charset: Charset = DEFAULT_CHARSET): String =
  String(readAllBytes(bufferSize), charset)
