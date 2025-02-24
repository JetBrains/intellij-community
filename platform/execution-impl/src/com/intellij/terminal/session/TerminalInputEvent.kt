// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.session

import com.intellij.terminal.session.dto.TerminalSizeDto
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Serializable
sealed interface TerminalInputEvent

@ApiStatus.Internal
@Serializable
data class TerminalResizeEvent(val newSize: TerminalSizeDto) : TerminalInputEvent

@ApiStatus.Internal
@Serializable
data class TerminalWriteBytesEvent(val bytes: ByteArray) : TerminalInputEvent {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as TerminalWriteBytesEvent

    return bytes.contentEquals(other.bytes)
  }

  override fun hashCode(): Int {
    return bytes.contentHashCode()
  }
}

@ApiStatus.Internal
@Serializable
data object TerminalCloseEvent : TerminalInputEvent

@ApiStatus.Internal
@Serializable
data object TerminalClearBufferEvent : TerminalInputEvent