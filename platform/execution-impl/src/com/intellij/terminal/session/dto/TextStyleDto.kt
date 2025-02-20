// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.session.dto

import com.jediterm.terminal.TextStyle
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Serializable
data class TextStyleDto(
  val foreground: TerminalColorDto?,
  val background: TerminalColorDto?,
  val options: List<TextStyleOptionDto>,
)

@ApiStatus.Internal
fun TextStyle.toDto(): TextStyleDto {
  val options = TextStyle.Option.entries.mapNotNull { opt ->
    if (hasOption(opt)) opt.toDto() else null
  }
  return TextStyleDto(
    foreground = foreground?.toDto(),
    background = background?.toDto(),
    options = if (options.isEmpty()) emptyList() else options
  )
}

@ApiStatus.Internal
fun TextStyleDto.toTextStyle(): TextStyle {
  val builder = TextStyle.Builder()
    .setForeground(foreground?.toColor())
    .setBackground(background?.toColor())
  for (option in options) {
    builder.setOption(option.toOption(), true)
  }
  return builder.build()
}