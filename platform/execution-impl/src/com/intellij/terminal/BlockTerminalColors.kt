// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal

import com.intellij.openapi.editor.colors.ColorKey
import com.intellij.openapi.editor.colors.TextAttributesKey
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object BlockTerminalColors {
  @JvmStatic val DEFAULT_FOREGROUND: ColorKey = ColorKey.createColorKey("BLOCK_TERMINAL_DEFAULT_FOREGROUND")
  @JvmStatic val DEFAULT_BACKGROUND: ColorKey = ColorKey.createColorKey("BLOCK_TERMINAL_DEFAULT_BACKGROUND")

  @JvmStatic val BLOCK_BACKGROUND_START: ColorKey = ColorKey.createColorKey("BLOCK_TERMINAL_BLOCK_BACKGROUND_START")
  @JvmStatic val BLOCK_BACKGROUND_END: ColorKey = ColorKey.createColorKey("BLOCK_TERMINAL_BLOCK_BACKGROUND_END")

  @JvmStatic val BLACK: TextAttributesKey = textAttributesKey("BLOCK_TERMINAL_BLACK")
  @JvmStatic val RED: TextAttributesKey = textAttributesKey("BLOCK_TERMINAL_RED")
  @JvmStatic val GREEN: TextAttributesKey = textAttributesKey("BLOCK_TERMINAL_GREEN")
  @JvmStatic val YELLOW: TextAttributesKey = textAttributesKey("BLOCK_TERMINAL_YELLOW")
  @JvmStatic val BLUE: TextAttributesKey = textAttributesKey("BLOCK_TERMINAL_BLUE")
  @JvmStatic val MAGENTA: TextAttributesKey = textAttributesKey("BLOCK_TERMINAL_MAGENTA")
  @JvmStatic val CYAN: TextAttributesKey = textAttributesKey("BLOCK_TERMINAL_CYAN")
  @JvmStatic val WHITE: TextAttributesKey = textAttributesKey("BLOCK_TERMINAL_WHITE")

  @JvmStatic val BLACK_BRIGHT: TextAttributesKey = textAttributesKey("BLOCK_TERMINAL_BLACK_BRIGHT")
  @JvmStatic val RED_BRIGHT: TextAttributesKey = textAttributesKey("BLOCK_TERMINAL_RED_BRIGHT")
  @JvmStatic val GREEN_BRIGHT: TextAttributesKey = textAttributesKey("BLOCK_TERMINAL_GREEN_BRIGHT")
  @JvmStatic val YELLOW_BRIGHT: TextAttributesKey = textAttributesKey("BLOCK_TERMINAL_YELLOW_BRIGHT")
  @JvmStatic val BLUE_BRIGHT: TextAttributesKey = textAttributesKey("BLOCK_TERMINAL_BLUE_BRIGHT")
  @JvmStatic val MAGENTA_BRIGHT: TextAttributesKey = textAttributesKey("BLOCK_TERMINAL_MAGENTA_BRIGHT")
  @JvmStatic val CYAN_BRIGHT: TextAttributesKey = textAttributesKey("BLOCK_TERMINAL_CYAN_BRIGHT")
  @JvmStatic val WHITE_BRIGHT: TextAttributesKey = textAttributesKey("BLOCK_TERMINAL_WHITE_BRIGHT")

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