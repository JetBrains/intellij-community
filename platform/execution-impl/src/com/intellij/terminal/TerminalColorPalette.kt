// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.markup.TextAttributes
import com.jediterm.core.Color
import com.jediterm.terminal.emulator.ColorPalette
import com.jediterm.terminal.ui.AwtTransformers

abstract class TerminalColorPalette : ColorPalette() {
  abstract val defaultForeground: Color
  abstract val defaultBackground: Color

  protected abstract fun getAttributesByColorIndex(index: Int): TextAttributes?

  override fun getForegroundByColorIndex(colorIndex: Int): Color {
    val attributes = getAttributesByColorIndex(colorIndex)
    val color = attributes?.foregroundColor ?: attributes?.backgroundColor
    return if (color != null) {
      AwtTransformers.fromAwtColor(color)!!
    }
    else {
      if (LOG.isDebugEnabled()) {
        LOG.debug("Default foreground color will be used for ANSI color index #$colorIndex")
      }
      defaultForeground
    }
  }

  override fun getBackgroundByColorIndex(colorIndex: Int): Color {
    val attributes = getAttributesByColorIndex(colorIndex)
    val color = attributes?.backgroundColor ?: attributes?.foregroundColor
    return if (color != null) {
      AwtTransformers.fromAwtColor(color)!!
    }
    else {
      if (LOG.isDebugEnabled()) {
        LOG.debug("Default background color will be used for ANSI color index #$colorIndex")
      }
      defaultBackground
    }
  }

  companion object {
    private val LOG: Logger = Logger.getInstance(TerminalColorPalette::class.java)
  }
}