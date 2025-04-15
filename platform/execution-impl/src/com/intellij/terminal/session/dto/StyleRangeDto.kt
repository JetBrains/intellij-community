// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.session.dto

import com.intellij.terminal.session.StyleRange
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Serializable
data class StyleRangeDto(
  val startOffset: Long,
  val endOffset: Long,
  val style: TextStyleDto,
)

@ApiStatus.Internal
fun StyleRange.toDto(): StyleRangeDto = StyleRangeDto(startOffset, endOffset, style.toDto())

@ApiStatus.Internal
fun StyleRangeDto.toStyleRange(): StyleRange = StyleRange(startOffset, endOffset, style.toTextStyle())