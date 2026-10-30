// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.colors.impl

import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.editor.colors.FontPreferences
import com.intellij.openapi.editor.impl.EditorFontFeatures
import com.intellij.util.ui.JdkConstants.FontStyle
import org.jetbrains.annotations.ApiStatus
import java.awt.Font

/**
 * The editor fonts and the console fonts of a colors scheme, in each [EditorFontType].
 * The instances are immutable and safe to use on any thread.
 * [with] gives the fonts of other font settings, or this instance when the settings are the same.
 *
 * @param fonts the font of each font type, at the index of its ordinal. A set from [empty] has no fonts.
 * @param useLigatures the ligatures of all fonts, or `null` for the ligatures of their preferences
 */
@ApiStatus.Internal
class EditorFonts private constructor(
  private val fonts: Array<Font>,
  private val editorFontName: String,
  private val editorFontSize: Float,
  private val editorPreferences: FontPreferences,
  private val consoleFontName: String,
  private val consoleFontSize: Float,
  private val consolePreferences: FontPreferences,
  private val useLigatures: Boolean?,
) {
  /** The last result of [withSize]. */
  @Volatile private var lastSized: EditorFonts? = null

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
    return create(
      editorFontName,
      editorFontSize,
      editorPreferences,
      consoleFontName,
      consoleFontSize,
      consolePreferences,
      useLigatures,
    )
  }

  /**
   * Returns these fonts with all of them at [size], for presentation mode.
   * It keeps the last result, so a repeated call with the same size creates no font.
   */
  fun withSize(size: Float): EditorFonts {
    check(fonts.isNotEmpty()) { "The empty font set has no fonts" }
    val last = lastSized
    if (last != null && last.editorFontSize == size) {
      return last
    }
    val sized = EditorFonts(
      fonts = Array(fonts.size) { index -> fonts[index].deriveFont(size) },
      editorFontName = editorFontName,
      editorFontSize = size,
      editorPreferences = editorPreferences,
      consoleFontName = consoleFontName,
      consoleFontSize = size,
      consolePreferences = consolePreferences,
      useLigatures = useLigatures,
    )
    lastSized = sized
    return sized
  }

  companion object {
    private val FONT_TYPES: Array<EditorFontType> = EditorFontType.entries.toTypedArray()

    /**
     * Returns a new font set without fonts. A scheme that has not built its fonts yet keeps it.
     * Each call gives a new instance, so [EditorFontCacheImpl] can tell its resets apart by identity:
     * a build that started before a reset then cannot publish its fonts.
     */
    @JvmStatic
    fun empty(): EditorFonts {
      return EditorFonts(
        fonts = emptyArray(),
        editorFontName = "",
        editorFontSize = 0f,
        editorPreferences = FontPreferencesSnapshot.EMPTY,
        consoleFontName = "",
        consoleFontSize = 0f,
        consolePreferences = FontPreferencesSnapshot.EMPTY,
        useLigatures = null,
      )
    }

    /**
     * Creates the fonts of the settings. The `when` has no `else` branch, so a new font type does not compile until it gets a font.
     */
    private fun create(
      editorFontName: String,
      editorFontSize: Float,
      editorPreferences: FontPreferences,
      consoleFontName: String,
      consoleFontSize: Float,
      consolePreferences: FontPreferences,
      useLigatures: Boolean?,
    ): EditorFonts {
      fun font(familyName: String, @FontStyle style: Int, fontSize: Float, preferences: FontPreferences): Font {
        return EditorFontFeatures.createFont(familyName, style, fontSize, preferences, useLigatures ?: preferences.useLigatures())
      }
      fun editorFont(@FontStyle style: Int): Font {
        return font(editorFontName, style, editorFontSize, editorPreferences)
      }
      fun consoleFont(@FontStyle style: Int): Font {
        return font(consoleFontName, style, consoleFontSize, consolePreferences)
      }
      val fonts = Array(FONT_TYPES.size) { index ->
        when (FONT_TYPES[index]) {
          EditorFontType.PLAIN -> editorFont(Font.PLAIN)
          EditorFontType.BOLD -> editorFont(Font.BOLD)
          EditorFontType.ITALIC -> editorFont(Font.ITALIC)
          EditorFontType.BOLD_ITALIC -> editorFont(Font.BOLD or Font.ITALIC)
          EditorFontType.CONSOLE_PLAIN -> consoleFont(Font.PLAIN)
          EditorFontType.CONSOLE_BOLD -> consoleFont(Font.BOLD)
          EditorFontType.CONSOLE_ITALIC -> consoleFont(Font.ITALIC)
          EditorFontType.CONSOLE_BOLD_ITALIC -> consoleFont(Font.BOLD or Font.ITALIC)
        }
      }
      return EditorFonts(
        fonts = fonts,
        editorFontName = editorFontName,
        editorFontSize = editorFontSize,
        editorPreferences = editorPreferences,
        consoleFontName = consoleFontName,
        consoleFontSize = consoleFontSize,
        consolePreferences = consolePreferences,
        useLigatures = useLigatures,
      )
    }
  }
}
