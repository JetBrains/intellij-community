// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.session

import com.intellij.execution.filters.HyperlinkInfo
import com.intellij.execution.impl.HyperlinkId
import com.intellij.openapi.editor.markup.TextAttributes
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
data class TerminalHyperlinksModelState(
  val hyperlinks: List<TerminalFilterResultInfo>
)

@ApiStatus.Internal
sealed class TerminalFilterResultInfo {
  abstract val id: TerminalHyperlinkId
  abstract val absoluteStartOffset: Long
  abstract val absoluteEndOffset: Long
  abstract val hyperlinkInfo: HyperlinkInfo?
}

@ApiStatus.Internal
data class TerminalHyperlinkInfo(
  override val id: TerminalHyperlinkId,
  override val hyperlinkInfo: HyperlinkInfo?,
  override val absoluteStartOffset: Long,
  override val absoluteEndOffset: Long,
  val style: TextAttributes?,
  val followedStyle: TextAttributes?,
  val hoveredStyle: TextAttributes?,
  val layer: Int,
) : TerminalFilterResultInfo()

@ApiStatus.Internal
data class TerminalHighlightingInfo(
  override val id: TerminalHyperlinkId,
  override val absoluteStartOffset: Long,
  override val absoluteEndOffset: Long,
  val style: TextAttributes?,
  val layer: Int,
) : TerminalFilterResultInfo() {
  override val hyperlinkInfo: HyperlinkInfo? = null
}

@ApiStatus.Internal
@Serializable
data class TerminalHyperlinkId(val value: Long) {
  fun toPlatformId(): HyperlinkId = HyperlinkId(value)
  override fun toString(): String = value.toString()
}
