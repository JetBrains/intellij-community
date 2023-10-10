// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal

import com.intellij.openapi.editor.colors.ColorKey
import com.intellij.openapi.editor.colors.TextAttributesKey
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object BlockTerminalColors {
  @JvmStatic val BLOCK_TERMINAL_DEFAULT_FOREGROUND: ColorKey = ColorKey.createColorKey("BLOCK_TERMINAL_DEFAULT_FOREGROUND")
  @JvmStatic val BLOCK_TERMINAL_DEFAULT_BACKGROUND: ColorKey = ColorKey.createColorKey("BLOCK_TERMINAL_DEFAULT_BACKGROUND")

  @JvmStatic val BLOCK_TERMINAL_BLACK: TextAttributesKey = textAttributesKey("BLOCK_TERMINAL_BLACK")
  @JvmStatic val BLOCK_TERMINAL_RED: TextAttributesKey = textAttributesKey("BLOCK_TERMINAL_RED")
  @JvmStatic val BLOCK_TERMINAL_GREEN: TextAttributesKey = textAttributesKey("BLOCK_TERMINAL_GREEN")
  @JvmStatic val BLOCK_TERMINAL_YELLOW: TextAttributesKey = textAttributesKey("BLOCK_TERMINAL_YELLOW")
  @JvmStatic val BLOCK_TERMINAL_BLUE: TextAttributesKey = textAttributesKey("BLOCK_TERMINAL_BLUE")
  @JvmStatic val BLOCK_TERMINAL_MAGENTA: TextAttributesKey = textAttributesKey("BLOCK_TERMINAL_MAGENTA")
  @JvmStatic val BLOCK_TERMINAL_CYAN: TextAttributesKey = textAttributesKey("BLOCK_TERMINAL_CYAN")
  @JvmStatic val BLOCK_TERMINAL_WHITE: TextAttributesKey = textAttributesKey("BLOCK_TERMINAL_WHITE")

  @JvmStatic val BLOCK_TERMINAL_BLACK_BRIGHT: TextAttributesKey = textAttributesKey("BLOCK_TERMINAL_BLACK_BRIGHT")
  @JvmStatic val BLOCK_TERMINAL_RED_BRIGHT: TextAttributesKey = textAttributesKey("BLOCK_TERMINAL_RED_BRIGHT")
  @JvmStatic val BLOCK_TERMINAL_GREEN_BRIGHT: TextAttributesKey = textAttributesKey("BLOCK_TERMINAL_GREEN_BRIGHT")
  @JvmStatic val BLOCK_TERMINAL_YELLOW_BRIGHT: TextAttributesKey = textAttributesKey("BLOCK_TERMINAL_YELLOW_BRIGHT")
  @JvmStatic val BLOCK_TERMINAL_BLUE_BRIGHT: TextAttributesKey = textAttributesKey("BLOCK_TERMINAL_BLUE_BRIGHT")
  @JvmStatic val BLOCK_TERMINAL_MAGENTA_BRIGHT: TextAttributesKey = textAttributesKey("BLOCK_TERMINAL_MAGENTA_BRIGHT")
  @JvmStatic val BLOCK_TERMINAL_CYAN_BRIGHT: TextAttributesKey = textAttributesKey("BLOCK_TERMINAL_CYAN_BRIGHT")
  @JvmStatic val BLOCK_TERMINAL_WHITE_BRIGHT: TextAttributesKey = textAttributesKey("BLOCK_TERMINAL_WHITE_BRIGHT")

  val KEYS: Array<TextAttributesKey>
    get() = arrayOf(
      BLOCK_TERMINAL_BLACK,
      BLOCK_TERMINAL_RED,
      BLOCK_TERMINAL_GREEN,
      BLOCK_TERMINAL_YELLOW,
      BLOCK_TERMINAL_BLUE,
      BLOCK_TERMINAL_MAGENTA,
      BLOCK_TERMINAL_CYAN,
      BLOCK_TERMINAL_WHITE,

      BLOCK_TERMINAL_BLACK_BRIGHT,
      BLOCK_TERMINAL_RED_BRIGHT,
      BLOCK_TERMINAL_GREEN_BRIGHT,
      BLOCK_TERMINAL_YELLOW_BRIGHT,
      BLOCK_TERMINAL_BLUE_BRIGHT,
      BLOCK_TERMINAL_MAGENTA_BRIGHT,
      BLOCK_TERMINAL_CYAN_BRIGHT,
      BLOCK_TERMINAL_WHITE_BRIGHT,
    )

  private fun textAttributesKey(name: String): TextAttributesKey {
    return TextAttributesKey.createTextAttributesKey(name)
  }
}