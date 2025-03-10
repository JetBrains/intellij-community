// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.session

import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.Flow
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface TerminalSession {
  /**
   * Use this channel to send the input events to the Terminal session.
   *
   * The channel is used here to mimic the behavior of the simple output stream:
   * 1. Send operation returns immediately without waiting for response from the receiver.
   * 2. It is guaranteed that events will be received in the same order they are sent.
   */
  suspend fun getInputChannel(): SendChannel<TerminalInputEvent>

  /**
   * Use this flow to handle the output events of the Terminal session.
   *
   * Underlying logic should continue reading the PTYs output stream only if there is some collector of this flow.
   */
  suspend fun getOutputFlow(): Flow<List<TerminalOutputEvent>>

  /**
   * Returns true of this session was terminated.
   * ([TerminalSessionTerminatedEvent] was received from the output flow)
   *
   * Can be accessed from any thread.
   */
  val isClosed: Boolean
}