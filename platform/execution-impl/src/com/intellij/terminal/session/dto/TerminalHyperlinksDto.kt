// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.session.dto

import com.intellij.execution.filters.HyperlinkInfo
import com.intellij.execution.impl.InlayProvider
import com.intellij.execution.ui.tryRecoverConsoleTextAttributesKey
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.terminal.session.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Serializable
sealed class TerminalFilterResultInfoDto {
  abstract val id: TerminalHyperlinkId
  abstract val absoluteStartOffset: Long
  abstract val absoluteEndOffset: Long
}

@ApiStatus.Internal
@Serializable
data class TerminalHyperlinkInfoDto(
  override val id: TerminalHyperlinkId,
  @Transient val hyperlinkInfo: HyperlinkInfo? = null,
  override val absoluteStartOffset: Long,
  override val absoluteEndOffset: Long,
  val style: TerminalTextAttributesDto?,
  val followedStyle: TerminalTextAttributesDto?,
  val hoveredStyle: TerminalTextAttributesDto?,
  val layer: Int,
) : TerminalFilterResultInfoDto()

@ApiStatus.Internal
@Serializable
data class TerminalHighlightingInfoDto(
  override val id: TerminalHyperlinkId,
  override val absoluteStartOffset: Long,
  override val absoluteEndOffset: Long,
  val style: TerminalTextAttributesDto?,
  val layer: Int,
) : TerminalFilterResultInfoDto()

@ApiStatus.Internal
@Serializable
data class TerminalInlayInfoDto(
  override val id: TerminalHyperlinkId,
  override val absoluteStartOffset: Long,
  override val absoluteEndOffset: Long,
  @Transient val inlayProvider: InlayProvider? = null,
) : TerminalFilterResultInfoDto()

@ApiStatus.Internal
@Serializable
data class TerminalTextAttributesDto(
  @Transient val attributes: TextAttributes? = null, // for the monolith only
  val attributesKey: TerminalTextAttributesKeyDto?,
) {
  fun toTextAttributes(): TextAttributes? =
    attributes ?: recoverFromKey()

  private fun recoverFromKey(): TextAttributes? =
    attributesKey?.toPlatformKey()?.let { EditorColorsManager.getInstance().globalScheme.getAttributes(it) }
}

@ApiStatus.Internal
@Serializable
data class TerminalTextAttributesKeyDto(
  val externalName: String,
  val fallback: TerminalTextAttributesKeyDto?,
) {
  fun toPlatformKey(): TextAttributesKey =
    if (fallback == null) {
      TextAttributesKey.createTextAttributesKey(externalName)
    }
    else {
      TextAttributesKey.createTextAttributesKey(externalName, fallback.toPlatformKey())
    }
}

@ApiStatus.Internal
fun TerminalFilterResultInfo.toDto(): TerminalFilterResultInfoDto =
  when (this) {
    is TerminalHyperlinkInfo -> TerminalHyperlinkInfoDto(
      id = id,
      hyperlinkInfo = hyperlinkInfo,
      absoluteStartOffset = absoluteStartOffset,
      absoluteEndOffset = absoluteEndOffset,
      style = style?.toDto(),
      followedStyle = followedStyle?.toDto(),
      hoveredStyle = hoveredStyle?.toDto(),
      layer = layer,
    )
    is TerminalHighlightingInfo -> TerminalHighlightingInfoDto(
      id = id,
      absoluteStartOffset = absoluteStartOffset,
      absoluteEndOffset = absoluteEndOffset,
      style = style?.toDto(),
      layer = layer,
    )
    is TerminalInlayInfo -> TerminalInlayInfoDto(
      id = id,
      absoluteStartOffset = absoluteStartOffset,
      absoluteEndOffset = absoluteEndOffset,
      inlayProvider = inlayProvider,
    )
  }


@ApiStatus.Internal
fun TextAttributes.toDto(): TerminalTextAttributesDto =
  TerminalTextAttributesDto(
    this,
    EditorColorsManager.getInstance().globalScheme.tryRecoverConsoleTextAttributesKey(this)?.toDto(),
  )

@ApiStatus.Internal
private fun TextAttributesKey.toDto(): TerminalTextAttributesKeyDto =
  TerminalTextAttributesKeyDto(
    externalName,
    fallbackAttributeKey?.toDto(),
  )

@ApiStatus.Internal
fun TerminalFilterResultInfoDto.toFilterResultInfo(): TerminalFilterResultInfo =
  when (this) {
    is TerminalHyperlinkInfoDto -> TerminalHyperlinkInfo(
      id = id,
      hyperlinkInfo = hyperlinkInfo,
      absoluteStartOffset = absoluteStartOffset,
      absoluteEndOffset = absoluteEndOffset,
      style = style?.toTextAttributes(),
      followedStyle = followedStyle?.toTextAttributes(),
      hoveredStyle = hoveredStyle?.toTextAttributes(),
      layer = layer,
    )
    is TerminalHighlightingInfoDto -> TerminalHighlightingInfo(
      id = id,
      absoluteStartOffset = absoluteStartOffset,
      absoluteEndOffset = absoluteEndOffset,
      style = style?.toTextAttributes(),
      layer = layer,
    )
    is TerminalInlayInfoDto -> TerminalInlayInfo(
      id = id,
      absoluteStartOffset = absoluteStartOffset,
      absoluteEndOffset = absoluteEndOffset,
      inlayProvider = inlayProvider,
    )
  }


@ApiStatus.Internal
@Serializable
data class TerminalHyperlinksModelStateDto(
  val hyperlinks: List<TerminalFilterResultInfoDto>,
)

@ApiStatus.Internal
fun TerminalHyperlinksModelState.toDto(): TerminalHyperlinksModelStateDto =
  TerminalHyperlinksModelStateDto(
    hyperlinks.map {
      it.toDto()
    }
  )
