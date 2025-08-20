// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.session

import com.intellij.execution.filters.HyperlinkInfo
import com.intellij.execution.impl.EditorTextDecorationId
import com.intellij.execution.impl.InlayProvider
import com.intellij.execution.impl.createTextDecorationId
import com.intellij.openapi.actionSystem.DataKey
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
data class TerminalInlayInfo(
  override val id: TerminalHyperlinkId,
  override val absoluteStartOffset: Long,
  override val absoluteEndOffset: Long,
  val inlayProvider: InlayProvider?,
) : TerminalFilterResultInfo() {
  override val hyperlinkInfo: HyperlinkInfo? = null
}

@ApiStatus.Internal
@Serializable
data class TerminalHyperlinkId(val value: Long) {
  override fun toString(): String = value.toString()
  companion object {
    @JvmStatic val KEY: DataKey<TerminalHyperlinkId> = DataKey.create("TerminalHyperlinkId")
  }
}

@ApiStatus.Internal
fun TerminalHyperlinkId.toPlatformId(): EditorTextDecorationId = createTextDecorationId(value)
@ApiStatus.Internal
fun EditorTextDecorationId.toTerminalId(): TerminalHyperlinkId = TerminalHyperlinkId(value)
