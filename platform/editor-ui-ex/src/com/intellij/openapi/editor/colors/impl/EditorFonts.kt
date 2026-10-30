// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.colors.impl

import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.editor.colors.FontPreferences
import com.intellij.openapi.editor.impl.EditorFontFeatures
import com.intellij.util.ui.JdkConstants.FontStyle
import org.jetbrains.annotations.ApiStatus
import java.awt.Font

private val FONT_TYPES: Array<EditorFontType> = EditorFontType.entries.toTypedArray()

/**
 * The editor fonts and the console fonts of a colors scheme, in each [EditorFontType].
 * The instances are immutable and safe to use on any thread.
 * [with] gives the fonts of other font settings, or this instance when the settings are the same.
 *
 * @param useLigatures the ligatures of all fonts, or `null` for the ligatures of their preferences
 * @param empty `true` only for a set from [empty]
 */
@ApiStatus.Internal
class EditorFonts private constructor(
  private val editorFontName: String,
  private val editorFontSize: Float,
  private val editorPreferences: FontPreferences,
  private val consoleFontName: String,
  private val consoleFontSize: Float,
  private val consolePreferences: FontPreferences,
  private val useLigatures: Boolean?,
  empty: Boolean,
) {
  /** The font of each font type, at the index of its ordinal. A set from [empty] has no fonts. */
  private val fonts: Array<Font> = if (empty) emptyArray() else Array(FONT_TYPES.size) { index -> createFont(FONT_TYPES[index]) }

  /**
   * Returns `true` for a set from [empty], which has no fonts.
   */
  fun isEmpty(): Boolean {
    return fonts.isEmpty()
  }

  /**
   * Returns the font of [fontType]. A set from [empty] has no fonts.
   */
  fun get(fontType: EditorFontType): Font {
    check(fonts.isNotEmpty()) { "The empty font set has no fonts" }
    return fonts[fontType.ordinal]
  }

  /**
   * Returns the fonts of these font settings, or this instance when it has the same settings.
   * The check compares the preferences by identity, so give it snapshots, see [FontPreferences.getSnapshot].
   *
   * @param useLigatures the ligatures of all fonts, or `null` for the ligatures of their preferences
   */
  fun with(
    editorFontName: String,
    editorFontSize: Float,
    editorPreferences: FontPreferences,
    consoleFontName: String,
    consoleFontSize: Float,
    consolePreferences: FontPreferences,
    useLigatures: Boolean?,
  ): EditorFonts {
    if (!isEmpty() &&
        editorPreferences === this.editorPreferences &&
        consolePreferences === this.consolePreferences &&
        editorFontName == this.editorFontName &&
        editorFontSize == this.editorFontSize &&
        consoleFontName == this.consoleFontName &&
        consoleFontSize == this.consoleFontSize &&
        useLigatures == this.useLigatures) {
      return this
    }
    return EditorFonts(
      editorFontName = editorFontName,
      editorFontSize = editorFontSize,
      editorPreferences = editorPreferences,
      consoleFontName = consoleFontName,
      consoleFontSize = consoleFontSize,
      consolePreferences = consolePreferences,
      useLigatures = useLigatures,
      empty = false,
    )
  }

  /**
   * Creates the font of [fontType]. The `when` has no `else` branch, so a new font type does not compile until it gets a font.
   */
  private fun createFont(fontType: EditorFontType): Font {
    return when (fontType) {
      EditorFontType.PLAIN -> createEditorFont(Font.PLAIN)
      EditorFontType.BOLD -> createEditorFont(Font.BOLD)
      EditorFontType.ITALIC -> createEditorFont(Font.ITALIC)
      EditorFontType.BOLD_ITALIC -> createEditorFont(Font.BOLD or Font.ITALIC)
      EditorFontType.CONSOLE_PLAIN -> createConsoleFont(Font.PLAIN)
      EditorFontType.CONSOLE_BOLD -> createConsoleFont(Font.BOLD)
      EditorFontType.CONSOLE_ITALIC -> createConsoleFont(Font.ITALIC)
      EditorFontType.CONSOLE_BOLD_ITALIC -> createConsoleFont(Font.BOLD or Font.ITALIC)
    }
  }

  private fun createEditorFont(@FontStyle style: Int): Font {
    return createFont(editorFontName, style, editorFontSize, editorPreferences)
  }

  private fun createConsoleFont(@FontStyle style: Int): Font {
    return createFont(consoleFontName, style, consoleFontSize, consolePreferences)
  }

  private fun createFont(familyName: String, @FontStyle style: Int, fontSize: Float, preferences: FontPreferences): Font {
    return EditorFontFeatures.createFont(familyName, style, fontSize, preferences, useLigatures ?: preferences.useLigatures())
  }

  companion object {
    /**
     * Returns a new font set without fonts. A scheme that has not built its fonts yet keeps it.
     * Each call gives a new instance, so [EditorFontCacheImpl] can tell its resets apart by identity:
     * a build that started before a reset then cannot publish its fonts.
     */
    @JvmStatic
    fun empty(): EditorFonts {
      return EditorFonts(
        editorFontName = "",
        editorFontSize = 0f,
        editorPreferences = FontPreferencesSnapshot.EMPTY,
        consoleFontName = "",
        consoleFontSize = 0f,
        consolePreferences = FontPreferencesSnapshot.EMPTY,
        useLigatures = null,
        empty = true,
      )
    }
  }
}
