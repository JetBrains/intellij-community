// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.session.dto

import com.jediterm.terminal.TerminalColor
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Serializable
data class TerminalColorDto(
  val colorIndex: Int?,
  val rgb: Int?,
)

@ApiStatus.Internal
fun TerminalColor.toDto(): TerminalColorDto {
  return if (isIndexed) {
    TerminalColorDto(colorIndex, null)
  }
  else {
    TerminalColorDto(null, toColor().rgb)
  }
}

@ApiStatus.Internal
fun TerminalColorDto.toColor(): TerminalColor {
  return if (colorIndex != null) {
    TerminalColor(colorIndex)
  }
  else {
    check(rgb != null) { "rgb value must not be null if colorIndex is null" }
    TerminalColor(
      (rgb shr 16) and 0xFF,
      (rgb shr 8) and 0xFF,
      rgb and 0xFF
    )
  }
}