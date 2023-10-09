// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.terminal

import com.intellij.execution.process.ColoredOutputTypeRegistryImpl
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.jediterm.core.Color
import com.jediterm.terminal.emulator.ColorPalette
import com.jediterm.terminal.ui.AwtTransformers

internal class JBTerminalSchemeColorPalette(private val colorsScheme: EditorColorsScheme) : ColorPalette() {
  override fun getForegroundByColorIndex(colorIndex: Int): Color {
    val attributes = colorsScheme.getAttributes(ColoredOutputTypeRegistryImpl.getAnsiColorKey(colorIndex))
    return when {
      attributes.foregroundColor != null -> AwtTransformers.fromAwtColor(attributes.foregroundColor)!!
      attributes.backgroundColor != null -> AwtTransformers.fromAwtColor(attributes.backgroundColor)!!
      else -> {
        if (LOG.isDebugEnabled()) {
          LOG.debug("Default foreground color will be used for ANSI color index #$colorIndex")
        }
        AwtTransformers.fromAwtColor(colorsScheme.defaultForeground)!!
      }
    }
  }

  override fun getBackgroundByColorIndex(colorIndex: Int): Color {
    val attributes = colorsScheme.getAttributes(ColoredOutputTypeRegistryImpl.getAnsiColorKey(colorIndex))
    return when {
      attributes.backgroundColor != null -> AwtTransformers.fromAwtColor(attributes.backgroundColor)!!
      attributes.foregroundColor != null -> AwtTransformers.fromAwtColor(attributes.foregroundColor)!!
      else -> {
        if (LOG.isDebugEnabled()) {
          LOG.debug("Default background color will be used for ANSI color index #$colorIndex")
        }
        AwtTransformers.fromAwtColor(colorsScheme.defaultBackground)!!
      }
    }
  }

  companion object {
    private val LOG = Logger.getInstance(JBTerminalSchemeColorPalette::class.java)
  }
}
