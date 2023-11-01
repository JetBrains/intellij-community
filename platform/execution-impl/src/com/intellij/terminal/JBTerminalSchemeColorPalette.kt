// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.terminal

import com.intellij.execution.process.ColoredOutputTypeRegistryImpl
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.colors.ColorKey
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.ExperimentalUI
import com.jediterm.core.Color
import com.jediterm.terminal.ui.AwtTransformers

internal class JBTerminalSchemeColorPalette(private val colorsScheme: EditorColorsScheme) : TerminalColorPalette() {
  private val colorKeys: Array<TextAttributesKey> = if (isBlockTerminalEnabled) {
    BlockTerminalColors.KEYS
  }
  else ColoredOutputTypeRegistryImpl.getAnsiColorKeys()

  private val defaultForegroundGetter: () -> java.awt.Color? = if (isBlockTerminalEnabled) {
    { colorsScheme.getColor(BlockTerminalColors.DEFAULT_FOREGROUND) }
  }
  else {
    { colorsScheme.getAttributes(ConsoleViewContentType.NORMAL_OUTPUT_KEY).foregroundColor }
  }

  private val defaultBackgroundKey: ColorKey = if (isBlockTerminalEnabled) {
    BlockTerminalColors.DEFAULT_BACKGROUND
  }
  else ConsoleViewContentType.CONSOLE_BACKGROUND_KEY

  override val defaultForeground: Color
    get() {
      val foregroundColor = defaultForegroundGetter()
      return AwtTransformers.fromAwtColor(foregroundColor ?: colorsScheme.defaultForeground)!!
    }
  override val defaultBackground: Color
    get() {
      val backgroundColor = colorsScheme.getColor(defaultBackgroundKey)
      return AwtTransformers.fromAwtColor(backgroundColor ?: colorsScheme.defaultBackground)!!
    }

  override fun getForegroundByColorIndex(colorIndex: Int): Color {
    val attributes = colorsScheme.getAttributes(getAnsiColorKey(colorIndex))
    val color = attributes.foregroundColor ?: attributes.backgroundColor
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
    val attributes = colorsScheme.getAttributes(getAnsiColorKey(colorIndex))
    val color = attributes.backgroundColor ?: attributes.foregroundColor
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

  private fun getAnsiColorKey(value: Int): TextAttributesKey {
    return if (value >= 16) {
      ConsoleViewContentType.NORMAL_OUTPUT_KEY
    }
    else colorKeys[value]
  }

  companion object {
    private val LOG = Logger.getInstance(JBTerminalSchemeColorPalette::class.java)

    private val isBlockTerminalEnabled: Boolean
      get() = ExperimentalUI.isNewUI() && Registry.`is`("ide.experimental.ui.new.terminal")
  }
}
