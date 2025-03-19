// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.session.dto

import com.jediterm.terminal.emulator.mouse.MouseFormat
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Serializable
enum class MouseFormatDto {
  MOUSE_FORMAT_XTERM_EXT,
  MOUSE_FORMAT_URXVT,
  MOUSE_FORMAT_SGR,
  MOUSE_FORMAT_XTERM;
}

@ApiStatus.Internal
fun MouseFormat.toDto(): MouseFormatDto {
  return when (this) {
    MouseFormat.MOUSE_FORMAT_XTERM_EXT -> MouseFormatDto.MOUSE_FORMAT_XTERM_EXT
    MouseFormat.MOUSE_FORMAT_URXVT -> MouseFormatDto.MOUSE_FORMAT_URXVT
    MouseFormat.MOUSE_FORMAT_SGR -> MouseFormatDto.MOUSE_FORMAT_SGR
    MouseFormat.MOUSE_FORMAT_XTERM -> MouseFormatDto.MOUSE_FORMAT_XTERM
  }
}

@ApiStatus.Internal
fun MouseFormatDto.toMouseFormat(): MouseFormat {
  return when (this) {
    MouseFormatDto.MOUSE_FORMAT_XTERM_EXT -> MouseFormat.MOUSE_FORMAT_XTERM_EXT
    MouseFormatDto.MOUSE_FORMAT_URXVT -> MouseFormat.MOUSE_FORMAT_URXVT
    MouseFormatDto.MOUSE_FORMAT_SGR -> MouseFormat.MOUSE_FORMAT_SGR
    MouseFormatDto.MOUSE_FORMAT_XTERM -> MouseFormat.MOUSE_FORMAT_XTERM
  }
}