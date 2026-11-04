// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.colors.impl

import com.intellij.application.options.EditorFontsConstants
import com.intellij.openapi.editor.colors.FontPreferences
import com.intellij.openapi.editor.colors.ModifiableFontPreferences
import org.jetbrains.annotations.ApiStatus
import java.util.function.Consumer

/**
 * Changes the fonts of a [ColorsSchemeSnapshot] and does not publish the result.
 * The font setters of [AbstractColorsScheme] and the read of a scheme use it, so both change the fonts in the same way.
 * A change of the editor font first gives the scheme its own editor font, a copy of [appFontPreferences].
 * A change of the console font first gives the scheme its own console font, a copy of the editor font.
 * A draft is for one thread.
 */
@ApiStatus.Internal
class ColorsSchemeFontDraft(
  private var current: ColorsSchemeSnapshot,
  private val appFontPreferences: FontPreferences,
) {
  /**
   * Returns the snapshot with the changes so far.
   */
  fun snapshot(): ColorsSchemeSnapshot {
    return current
  }

  /**
   * Returns the editor font preferences of the snapshot.
   * They are the own preferences of the scheme, or [appFontPreferences].
   */
  fun editorFontPreferences(): FontPreferences {
    if (current.useAppFontPreferencesInEditor()) {
      return appFontPreferences
    }
    return current.fontPreferences()
  }

  /**
   * Gives the scheme its own editor font, a copy of [appFontPreferences], if the scheme uses the application font.
   */
  private fun ensureOwnFont() {
    if (current.useAppFontPreferencesInEditor()) {
      current = current
        .withFontPreferences(copyOf(appFontPreferences))
        .withUseAppFontPreferencesInEditor(false)
    }
  }

  /**
   * Gives the scheme its own console font, a copy of the editor font, if the console uses the editor font.
   */
  private fun ensureOwnConsoleFont() {
    if (current.useEditorFontPreferencesInConsole()) {
      current = current
        .withConsoleFontPreferences(copyOf(editorFontPreferences()))
        .withUseEditorFontPreferencesInConsole(false)
    }
  }

  /**
   * Runs [changes] on the own editor font of the scheme.
   */
  fun changeFont(changes: Consumer<in ModifiableFontPreferences>) {
    ensureOwnFont()
    current = current.withFontPreferences(current.fontPreferences().withChanges(changes))
  }

  /**
   * Runs [changes] on the own console font of the scheme.
   */
  fun changeConsoleFont(changes: Consumer<in ModifiableFontPreferences>) {
    ensureOwnConsoleFont()
    current = current.withConsoleFontPreferences(current.consoleFontPreferences().withChanges(changes))
  }

  /**
   * Copies [preferences] into the own editor font of the scheme.
   */
  fun setFontPreferences(preferences: FontPreferences) {
    changeFont { draft -> preferences.copyTo(draft) }
  }

  /**
   * Replaces the editor font families with [fontName] and keeps the font size and the ligatures.
   */
  fun setEditorFontName(fontName: String) {
    changeFont { draft ->
      val fontSize = draft.getSize2D(draft.fontFamily)
      val useLigatures = draft.useLigatures()
      draft.clear()
      draft.register(fontName, fontSize)
      draft.setUseLigatures(useLigatures)
    }
  }

  fun setEditorFontSize(fontSize: Float) {
    val fixedFontSize = EditorFontsConstants.checkAndFixEditorFontSize(fontSize)
    changeFont { draft -> draft.register(draft.fontFamily, fixedFontSize) }
  }

  fun setLineSpacing(lineSpacing: Float) {
    changeFont { draft -> draft.lineSpacing = lineSpacing }
  }

  fun setUseLigatures(useLigatures: Boolean) {
    changeFont { draft -> draft.setUseLigatures(useLigatures) }
  }

  /**
   * Copies [preferences] into the own console font of the scheme.
   */
  fun setConsoleFontPreferences(preferences: FontPreferences) {
    changeConsoleFont { draft -> preferences.copyTo(draft) }
  }

  /**
   * Replaces the console font families with [fontName] and keeps the console font size.
   */
  fun setConsoleFontName(fontName: String) {
    ensureOwnConsoleFont()
    val editorPreferences = editorFontPreferences()
    val editorFontSize = editorPreferences.getSize2D(editorPreferences.fontFamily)
    val fontSize = AbstractColorsScheme.consoleFontSize2D(current.consoleFontPreferences(), editorFontSize)
    changeConsoleFont { draft ->
      draft.clear()
      draft.register(fontName, fontSize)
    }
  }

  fun setConsoleFontSize(fontSize: Float) {
    val fixedFontSize = EditorFontsConstants.checkAndFixEditorFontSize(fontSize)
    changeConsoleFont { draft -> draft.register(draft.fontFamily, fixedFontSize) }
  }

  fun setConsoleLineSpacing(lineSpacing: Float) {
    changeConsoleFont { draft -> draft.lineSpacing = lineSpacing }
  }

  fun setConsoleUseLigatures(useLigatures: Boolean) {
    changeConsoleFont { draft -> draft.setUseLigatures(useLigatures) }
  }

  companion object {
    private fun copyOf(preferences: FontPreferences): FontPreferencesSnapshot {
      val copy = FontPreferencesImpl()
      preferences.copyTo(copy)
      return copy.snapshot as FontPreferencesSnapshot
    }
  }
}
