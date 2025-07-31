// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.session

import com.intellij.terminal.session.dto.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.jetbrains.annotations.ApiStatus
import kotlin.time.TimeMark

@ApiStatus.Internal
@Serializable
sealed interface TerminalOutputEvent

@ApiStatus.Internal
@Serializable
data class TerminalContentUpdatedEvent(
  val text: String,
  val styles: List<StyleRangeDto>,
  val startLineLogicalIndex: Long,
  /** This value is used only on Backend. It is always null on the Frontend. */
  @Transient
  val readTime: TimeMark? = null,
) : TerminalOutputEvent

@ApiStatus.Internal
@Serializable
data class TerminalCursorPositionChangedEvent(
  val logicalLineIndex: Long,
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
  val outputHyperlinksState: TerminalHyperlinksModelStateDto?,
  val alternateBufferHyperlinksState: TerminalHyperlinksModelStateDto?,
) : TerminalOutputEvent

// Shell Integration Events

@ApiStatus.Internal
@Serializable
sealed interface TerminalShellIntegrationEvent : TerminalOutputEvent

@ApiStatus.Internal
@Serializable
data class TerminalCommandStartedEvent(val command: String) : TerminalShellIntegrationEvent

@ApiStatus.Internal
@Serializable
data class TerminalCommandFinishedEvent(val command: String, val exitCode: Int, val currentDirectory: String) : TerminalShellIntegrationEvent

@ApiStatus.Internal
@Serializable
data object TerminalPromptStartedEvent : TerminalShellIntegrationEvent

@ApiStatus.Internal
@Serializable
data object TerminalPromptFinishedEvent : TerminalShellIntegrationEvent

@ApiStatus.Internal
@Serializable
data class TerminalAliasesReceivedEvent(val aliases: TerminalAliasesInfo) : TerminalShellIntegrationEvent

/**
 * A backend-only event indicating that there may be hyperlink events to pull from the highlighter.
 */
@ApiStatus.Internal
@Serializable
data class TerminalHyperlinksHeartbeatEvent(val isInAlternateBuffer: Boolean) : TerminalOutputEvent

/**
 * A change in terminal hyperlinks.
 *
 * If there are a lot of links, they may arrive in batches with the same events having the same [documentModificationStamp].
 * In this case, only the first event of a batch will have this property set.
 * When the model receives the first event, it removes the old hyperlinks from that offset onwards.
 * The next events will only add new hyperlinks to the model.
 * The last event always has an empty hyperlink list and used to indicate that the hyperlink processing has finished.
 */
@ApiStatus.Internal
@Serializable
data class TerminalHyperlinksChangedEvent(
  /**
   * Indicates which of the two terminal documents was changes.
   */
  val isInAlternateBuffer: Boolean,
  /**
   * The document modification stamp at the time a snapshot was taken to compute hyperlinks.
   */
  val documentModificationStamp: Long,
  /**
   * The absolute offset (document offset plus the trimmed count) from which the links were updated.
   *
   * Only set for the first event in a batch.
   */
  val removeFromOffset: Long?,
  /**
   * The newly computed hyperlinks.
   *
   * May be empty for the first event in a batch, always empty for the last event.
   */
  val hyperlinks: List<TerminalFilterResultInfoDto>,
) : TerminalOutputEvent
