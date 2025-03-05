// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.session.dto

import com.jediterm.terminal.TextStyle
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Serializable
enum class TextStyleOptionDto {
  BOLD,
  ITALIC,
  SLOW_BLINK,
  RAPID_BLINK,
  DIM,
  INVERSE,
  UNDERLINED,
  HIDDEN;
}

@ApiStatus.Internal
fun TextStyle.Option.toDto(): TextStyleOptionDto {
  return when (this) {
    TextStyle.Option.BOLD -> TextStyleOptionDto.BOLD
    TextStyle.Option.ITALIC -> TextStyleOptionDto.ITALIC
    TextStyle.Option.SLOW_BLINK -> TextStyleOptionDto.SLOW_BLINK
    TextStyle.Option.RAPID_BLINK -> TextStyleOptionDto.RAPID_BLINK
    TextStyle.Option.DIM -> TextStyleOptionDto.DIM
    TextStyle.Option.INVERSE -> TextStyleOptionDto.INVERSE
    TextStyle.Option.UNDERLINED -> TextStyleOptionDto.UNDERLINED
    TextStyle.Option.HIDDEN -> TextStyleOptionDto.HIDDEN
  }
}

@ApiStatus.Internal
fun TextStyleOptionDto.toOption(): TextStyle.Option {
  return when (this) {
    TextStyleOptionDto.BOLD -> TextStyle.Option.BOLD
    TextStyleOptionDto.ITALIC -> TextStyle.Option.ITALIC
    TextStyleOptionDto.SLOW_BLINK -> TextStyle.Option.SLOW_BLINK
    TextStyleOptionDto.RAPID_BLINK -> TextStyle.Option.RAPID_BLINK
    TextStyleOptionDto.DIM -> TextStyle.Option.DIM
    TextStyleOptionDto.INVERSE -> TextStyle.Option.INVERSE
    TextStyleOptionDto.UNDERLINED -> TextStyle.Option.UNDERLINED
    TextStyleOptionDto.HIDDEN -> TextStyle.Option.HIDDEN
  }
}