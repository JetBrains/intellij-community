// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.session.dto

import com.intellij.terminal.session.TerminalOutputModelState
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@Serializable
@ApiStatus.Internal
data class TerminalOutputModelStateDto(
  val text: String,
  val trimmedLinesCount: Int,
  val trimmedCharsCount: Int,
  val cursorOffset: Int,
  val highlightings: List<StyleRangeDto>,
)

@ApiStatus.Internal
fun TerminalOutputModelState.toDto(): TerminalOutputModelStateDto {
  return TerminalOutputModelStateDto(
    text = text,
    trimmedLinesCount = trimmedLinesCount,
    trimmedCharsCount = trimmedCharsCount,
    cursorOffset = cursorOffset,
    highlightings = highlightings.map { it.toDto() }
  )
}

@ApiStatus.Internal
fun TerminalOutputModelStateDto.toState(): TerminalOutputModelState {
  return TerminalOutputModelState(
    text = text,
    trimmedLinesCount = trimmedLinesCount,
    trimmedCharsCount = trimmedCharsCount,
    cursorOffset = cursorOffset,
    highlightings = highlightings.map { it.toStyleRange() }
  )
}