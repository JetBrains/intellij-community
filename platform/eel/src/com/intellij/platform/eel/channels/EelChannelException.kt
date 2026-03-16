// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Experimental

package com.intellij.platform.eel.channels

import com.intellij.platform.eel.EelError
import org.jetbrains.annotations.ApiStatus
import java.io.IOException

@ApiStatus.Experimental
sealed class EelChannelException : IOException, EelError {
  constructor(cause: Throwable?) : super(cause)
  constructor(message: String) : super(message)
  constructor(message: String, cause: Throwable?) : super(message, cause)
}

/**
 * This error means that the channel is broken. It's not possible to write into the channel.
 * Also, there's no need to call [EelSendChannel.close], though calling it should do nothing.
 *
 * API users are not supposed to write complicated logic for handling this exception.
 * If it happened, it's enough to log it and stop processing the stream.
 * An exception may have a [cause], but it is intended only for debugging purposes
 * and is not supposed to be handled.
 */
@ApiStatus.Experimental
class EelSendChannelException : EelChannelException {
  val channel: EelSendChannel

  constructor(channel: EelSendChannel, cause: Throwable?) : super(cause) {
    this.channel = channel
  }

  constructor(channel: EelSendChannel, message: String) : super(message) {
    this.channel = channel
  }

  constructor(channel: EelSendChannel, message: String, cause: Throwable?) : super(message, cause) {
    this.channel = channel
  }
}

/**
 * This error means that the channel is broken. It's not possible to read from the channel.
 * Also, there's no need to call [EelReceiveChannel.closeForReceive], though calling it should do nothing.
 *
 * API users are not supposed to write complicated logic for handling this exception.
 * If it happened, it's enough to log it and stop processing the stream.
 * An exception may have a [cause], but it is intended only for debugging purposes
 * and is not supposed to be handled.
 */
@ApiStatus.Experimental
class EelReceiveChannelException : EelChannelException {
  val channel: EelReceiveChannel

  constructor(channel: EelReceiveChannel, cause: Throwable?) : super(cause) {
    this.channel = channel
  }

  constructor(channel: EelReceiveChannel, message: String) : super(message) {
    this.channel = channel
  }

  constructor(channel: EelReceiveChannel, message: String, cause: Throwable?) : super(message, cause) {
    this.channel = channel
  }
}