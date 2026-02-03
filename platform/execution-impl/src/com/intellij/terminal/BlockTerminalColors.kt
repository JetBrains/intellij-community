// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal

import com.intellij.openapi.editor.colors.ColorKey
import com.intellij.openapi.editor.colors.TextAttributesKey
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object BlockTerminalColors {
  @JvmField val DEFAULT_FOREGROUND: ColorKey = ColorKey.createColorKey("BLOCK_TERMINAL_DEFAULT_FOREGROUND")
  @JvmField val DEFAULT_BACKGROUND: ColorKey = ColorKey.createColorKey("BLOCK_TERMINAL_DEFAULT_BACKGROUND")

  @JvmField val BLOCK_BACKGROUND_START: ColorKey = ColorKey.createColorKey("BLOCK_TERMINAL_BLOCK_BACKGROUND_START")
  @JvmField val BLOCK_BACKGROUND_END: ColorKey = ColorKey.createColorKey("BLOCK_TERMINAL_BLOCK_BACKGROUND_END")

  @JvmField val SELECTED_BLOCK_BACKGROUND: ColorKey = ColorKey.createColorKey("BLOCK_TERMINAL_SELECTED_BLOCK_BACKGROUND")
  @JvmField val SELECTED_BLOCK_STROKE_COLOR: ColorKey = ColorKey.createColorKey("BLOCK_TERMINAL_SELECTED_BLOCK_STROKE_COLOR")

  @JvmField val HOVERED_BLOCK_BACKGROUND_START: ColorKey = ColorKey.createColorKey("BLOCK_TERMINAL_HOVERED_BLOCK_BACKGROUND_START")
  @JvmField val HOVERED_BLOCK_BACKGROUND_END: ColorKey = ColorKey.createColorKey("BLOCK_TERMINAL_HOVERED_BLOCK_BACKGROUND_END")

  @JvmField val INACTIVE_SELECTED_BLOCK_BACKGROUND: ColorKey = ColorKey.createColorKey("BLOCK_TERMINAL_INACTIVE_SELECTED_BLOCK_BACKGROUND")
  @JvmField val INACTIVE_SELECTED_BLOCK_STROKE_COLOR: ColorKey = ColorKey.createColorKey("BLOCK_TERMINAL_INACTIVE_SELECTED_BLOCK_STROKE_COLOR")

  @JvmField val ERROR_BLOCK_STROKE_COLOR: ColorKey = ColorKey.createColorKey("BLOCK_TERMINAL_ERROR_BLOCK_STROKE_COLOR")

  @JvmField val PROMPT_SEPARATOR_COLOR: ColorKey = ColorKey.createColorKey("BLOCK_TERMINAL_PROMPT_SEPARATOR_COLOR")

  @JvmField val COMMAND: TextAttributesKey = textAttributesKey("BLOCK_TERMINAL_COMMAND")

  @JvmField val SEARCH_ENTRY: TextAttributesKey = textAttributesKey("BLOCK_TERMINAL_SEARCH_ENTRY")
  @JvmField val CURRENT_SEARCH_ENTRY: TextAttributesKey = textAttributesKey("BLOCK_TERMINAL_CURRENT_SEARCH_ENTRY")

  @JvmField val GENERATE_COMMAND_PLACEHOLDER_FOREGROUND: ColorKey = ColorKey.createColorKey("BLOCK_TERMINAL_GENERATE_COMMAND_PLACEHOLDER_FOREGROUND")
  @JvmField val GENERATE_COMMAND_CARET_COLOR: ColorKey = ColorKey.createColorKey("BLOCK_TERMINAL_GENERATE_COMMAND_CARET_COLOR")
  @JvmField val GENERATE_COMMAND_PROMPT_TEXT: TextAttributesKey = TextAttributesKey.createTextAttributesKey("BLOCK_TERMINAL_GENERATE_COMMAND_PROMPT_TEXT")

  @JvmField val BLACK: TextAttributesKey = textAttributesKey("BLOCK_TERMINAL_BLACK")
  @JvmField val RED: TextAttributesKey = textAttributesKey("BLOCK_TERMINAL_RED")
  @JvmField val GREEN: TextAttributesKey = textAttributesKey("BLOCK_TERMINAL_GREEN")
  @JvmField val YELLOW: TextAttributesKey = textAttributesKey("BLOCK_TERMINAL_YELLOW")
  @JvmField val BLUE: TextAttributesKey = textAttributesKey("BLOCK_TERMINAL_BLUE")
  @JvmField val MAGENTA: TextAttributesKey = textAttributesKey("BLOCK_TERMINAL_MAGENTA")
  @JvmField val CYAN: TextAttributesKey = textAttributesKey("BLOCK_TERMINAL_CYAN")
  @JvmField val WHITE: TextAttributesKey = textAttributesKey("BLOCK_TERMINAL_WHITE")

  @JvmField val BLACK_BRIGHT: TextAttributesKey = textAttributesKey("BLOCK_TERMINAL_BLACK_BRIGHT")
  @JvmField val RED_BRIGHT: TextAttributesKey = textAttributesKey("BLOCK_TERMINAL_RED_BRIGHT")
  @JvmField val GREEN_BRIGHT: TextAttributesKey = textAttributesKey("BLOCK_TERMINAL_GREEN_BRIGHT")
  @JvmField val YELLOW_BRIGHT: TextAttributesKey = textAttributesKey("BLOCK_TERMINAL_YELLOW_BRIGHT")
  @JvmField val BLUE_BRIGHT: TextAttributesKey = textAttributesKey("BLOCK_TERMINAL_BLUE_BRIGHT")
  @JvmField val MAGENTA_BRIGHT: TextAttributesKey = textAttributesKey("BLOCK_TERMINAL_MAGENTA_BRIGHT")
  @JvmField val CYAN_BRIGHT: TextAttributesKey = textAttributesKey("BLOCK_TERMINAL_CYAN_BRIGHT")
  @JvmField val WHITE_BRIGHT: TextAttributesKey = textAttributesKey("BLOCK_TERMINAL_WHITE_BRIGHT")

  val KEYS: Array<TextAttributesKey>
    get() = arrayOf(
      BLACK,
      RED,
      GREEN,
      YELLOW,
      BLUE,
      MAGENTA,
      CYAN,
      WHITE,

      BLACK_BRIGHT,
      RED_BRIGHT,
      GREEN_BRIGHT,
      YELLOW_BRIGHT,
      BLUE_BRIGHT,
      MAGENTA_BRIGHT,
      CYAN_BRIGHT,
      WHITE_BRIGHT,
    )

  private fun textAttributesKey(name: String): TextAttributesKey {
    return TextAttributesKey.createTextAttributesKey(name)
  }
}