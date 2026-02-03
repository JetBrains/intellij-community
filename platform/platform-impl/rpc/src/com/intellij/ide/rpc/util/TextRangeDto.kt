// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.rpc.util

import com.intellij.openapi.util.TextRange
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Serializable
data class TextRangeDto(
  val startOffset: Int,
  val endOffset: Int,
)

@ApiStatus.Internal
fun TextRange.toRpc(): TextRangeDto = TextRangeDto(startOffset, endOffset)

@ApiStatus.Internal
fun TextRangeDto.textRange(): TextRange = TextRange(startOffset, endOffset)