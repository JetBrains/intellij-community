// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.session.dto

import com.jediterm.terminal.CursorShape
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Serializable
enum class CursorShapeDto {
  BLINK_BLOCK,
  STEADY_BLOCK,
  BLINK_UNDERLINE,
  STEADY_UNDERLINE,
  BLINK_VERTICAL_BAR,
  STEADY_VERTICAL_BAR;
}

@ApiStatus.Internal
fun CursorShape.toDto(): CursorShapeDto {
  return when (this) {
    CursorShape.BLINK_BLOCK -> CursorShapeDto.BLINK_BLOCK
    CursorShape.STEADY_BLOCK -> CursorShapeDto.STEADY_BLOCK
    CursorShape.BLINK_UNDERLINE -> CursorShapeDto.BLINK_UNDERLINE
    CursorShape.STEADY_UNDERLINE -> CursorShapeDto.STEADY_UNDERLINE
    CursorShape.BLINK_VERTICAL_BAR -> CursorShapeDto.BLINK_VERTICAL_BAR
    CursorShape.STEADY_VERTICAL_BAR -> CursorShapeDto.STEADY_VERTICAL_BAR
  }
}

@ApiStatus.Internal
fun CursorShapeDto.toCursorShape(): CursorShape {
  return when (this) {
    CursorShapeDto.BLINK_BLOCK -> CursorShape.BLINK_BLOCK
    CursorShapeDto.STEADY_BLOCK -> CursorShape.STEADY_BLOCK
    CursorShapeDto.BLINK_UNDERLINE -> CursorShape.BLINK_UNDERLINE
    CursorShapeDto.STEADY_UNDERLINE -> CursorShape.STEADY_UNDERLINE
    CursorShapeDto.BLINK_VERTICAL_BAR -> CursorShape.BLINK_VERTICAL_BAR
    CursorShapeDto.STEADY_VERTICAL_BAR -> CursorShape.STEADY_VERTICAL_BAR
  }
}