// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.session

import com.intellij.terminal.session.dto.StyleRangeDto
import com.intellij.terminal.session.dto.TerminalBlocksModelStateDto
import com.intellij.terminal.session.dto.TerminalOutputModelStateDto
import com.intellij.terminal.session.dto.TerminalStateDto
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Serializable
sealed interface TerminalOutputEvent

@ApiStatus.Internal
@Serializable
data class TerminalContentUpdatedEvent(
  val text: String,
  val styles: List<StyleRangeDto>,
  val startLineLogicalIndex: Int,
) : TerminalOutputEvent

@ApiStatus.Internal
@Serializable
data class TerminalCursorPositionChangedEvent(
  val logicalLineIndex: Int,
  val columnIndex: Int,
) : TerminalOutputEvent

@ApiStatus.Internal
@Serializable
data class TerminalStateChangedEvent(val state: TerminalStateDto) : TerminalOutputEvent

@ApiStatus.Internal
@Serializable
data object TerminalBeepEvent : TerminalOutputEvent

@ApiStatus.Internal
@Serializable
data object TerminalSessionTerminatedEvent : TerminalOutputEvent

@ApiStatus.Internal
@Serializable
data class TerminalInitialStateEvent(
  val sessionState: TerminalStateDto,
  val outputModelState: TerminalOutputModelStateDto,
  val alternateBufferState: TerminalOutputModelStateDto,
  val blocksModelState: TerminalBlocksModelStateDto,
) : TerminalOutputEvent

// Shell Integration Events

@ApiStatus.Internal
@Serializable
sealed interface TerminalShellIntegrationEvent : TerminalOutputEvent

@ApiStatus.Internal
@Serializable
data object TerminalShellIntegrationInitializedEvent : TerminalShellIntegrationEvent

@ApiStatus.Internal
@Serializable
data class TerminalCommandStartedEvent(val command: String) : TerminalShellIntegrationEvent

@ApiStatus.Internal
@Serializable
data class TerminalCommandFinishedEvent(val command: String, val exitCode: Int) : TerminalShellIntegrationEvent

@ApiStatus.Internal
@Serializable
data object TerminalPromptStartedEvent : TerminalShellIntegrationEvent

@ApiStatus.Internal
@Serializable
data object TerminalPromptFinishedEvent : TerminalShellIntegrationEvent