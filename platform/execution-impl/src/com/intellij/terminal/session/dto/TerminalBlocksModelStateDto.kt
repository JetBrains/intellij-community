// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.session.dto

import com.intellij.terminal.session.TerminalBlocksModelState
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@Serializable
@ApiStatus.Internal
data class TerminalBlocksModelStateDto(
  val blocks: List<TerminalOutputBlockDto>,
  val blockIdCounter: Int,
)

@ApiStatus.Internal
fun TerminalBlocksModelState.toDto(): TerminalBlocksModelStateDto {
  return TerminalBlocksModelStateDto(blocks.map { it.toDto() }, blockIdCounter)
}

@ApiStatus.Internal
fun TerminalBlocksModelStateDto.toState(): TerminalBlocksModelState {
  return TerminalBlocksModelState(blocks.map { it.toBlock() }, blockIdCounter)
}