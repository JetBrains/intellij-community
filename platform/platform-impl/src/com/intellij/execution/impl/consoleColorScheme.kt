// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("ConsoleColorSchemeKt")

package com.intellij.execution.impl

import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.editor.colors.FontPreferences
import com.intellij.openapi.editor.colors.impl.DelegateColorScheme
import org.jetbrains.annotations.ApiStatus
import java.awt.Color
import java.awt.Font

/**
 * Creates a color scheme which uses the console font settings and the console background of [scheme].
 */
@ApiStatus.Internal
fun createConsoleColorScheme(scheme: EditorColorsScheme): DelegateColorScheme {
  return object : DelegateColorScheme(scheme) {
    override fun getDefaultBackground(): Color = getColor(ConsoleViewContentType.CONSOLE_BACKGROUND_KEY) ?: super.getDefaultBackground()

    override fun getFontPreferences(): FontPreferences = consoleFontPreferences

    override fun getEditorFontSize(): Int = consoleFontSize

    override fun getEditorFontSize2D(): Float = consoleFontSize2D

    override fun getEditorFontName(): String = consoleFontName

    override fun getLineSpacing(): Float = consoleLineSpacing

    override fun getFont(key: EditorFontType?): Font = super.getFont(EditorFontType.getConsoleType(key))

    override fun setEditorFontSize(fontSize: Int) {
      consoleFontSize = fontSize
    }

    override fun setEditorFontSize(fontSize: Float) {
      setConsoleFontSize(fontSize)
    }
  }
}
