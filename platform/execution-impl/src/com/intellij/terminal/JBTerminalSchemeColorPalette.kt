// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.terminal

import com.intellij.execution.process.ColoredOutputTypeRegistryImpl
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.markup.TextAttributes
import com.jediterm.core.Color
import com.jediterm.terminal.ui.AwtTransformers

internal class JBTerminalSchemeColorPalette(private val colorsScheme: EditorColorsScheme) : TerminalColorPalette() {
  override val defaultForeground: Color
    get() {
      val foregroundColor = colorsScheme.getAttributes(ConsoleViewContentType.NORMAL_OUTPUT_KEY).foregroundColor
      return AwtTransformers.fromAwtColor(foregroundColor ?: colorsScheme.defaultForeground)!!
    }
  override val defaultBackground: Color
    get() {
      val backgroundColor = colorsScheme.getColor(ConsoleViewContentType.CONSOLE_BACKGROUND_KEY)
      return AwtTransformers.fromAwtColor(backgroundColor ?: colorsScheme.defaultBackground)!!
    }

  override fun getAttributesByColorIndex(index: Int): TextAttributes {
    val key = ColoredOutputTypeRegistryImpl.getAnsiColorKey(index)
    return colorsScheme.getAttributes(key)
  }
}
