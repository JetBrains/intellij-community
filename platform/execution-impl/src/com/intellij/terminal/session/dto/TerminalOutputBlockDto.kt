// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.session.dto

import com.intellij.terminal.session.TerminalOutputBlock
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@Serializable
@ApiStatus.Internal
data class TerminalOutputBlockDto(
  val id: Int,
  val startOffset: Int,
  val commandStartOffset: Int,
  val outputStartOffset: Int,
  val endOffset: Int,
  val exitCode: Int?,
)

@ApiStatus.Internal
fun TerminalOutputBlock.toDto(): TerminalOutputBlockDto {
  return TerminalOutputBlockDto(
    id = id,
    startOffset = startOffset,
    commandStartOffset = commandStartOffset,
    outputStartOffset = outputStartOffset,
    endOffset = endOffset,
    exitCode = exitCode
  )
}

@ApiStatus.Internal
fun TerminalOutputBlockDto.toBlock(): TerminalOutputBlock {
  return TerminalOutputBlock(
    id = id,
    startOffset = startOffset,
    commandStartOffset = commandStartOffset,
    outputStartOffset = outputStartOffset,
    endOffset = endOffset,
    exitCode = exitCode
  )
}