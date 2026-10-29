// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.editor.colors.FontPreferences
import com.intellij.util.ui.JdkConstants
import com.jetbrains.JBR
import org.jetbrains.annotations.ApiStatus
import java.awt.Font
import java.awt.font.TextAttribute

/**
 * Applies the OpenType features of the editor fonts.
 * These are the ligatures and the character variants.
 */
@ApiStatus.Internal
object EditorFontFeatures {
  private val LIGATURES_ON: Map<TextAttribute, Int> = mapOf(TextAttribute.LIGATURES to TextAttribute.LIGATURES_ON)

  /**
   * Creates a font of the family with the subfamilies and the character variants of [preferences].
   * The ligatures follow [useLigatures], so a caller can override the ligatures of the preferences.
   */
  @JvmStatic
  fun createFont(
    familyName: String,
    @JdkConstants.FontStyle style: Int,
    size: Float,
    preferences: FontPreferences,
    useLigatures: Boolean,
  ): Font {
    val font = FontFamilyService.getFont(familyName, preferences.regularSubFamily, preferences.boldSubFamily, style, size)
    return withFeatures(font, useLigatures, preferences.characterVariants)
  }

  /**
   * Returns the font with the ligatures on.
   * When [enabled] is false, it returns the same font.
   */
  @JvmStatic
  fun withLigatures(font: Font, enabled: Boolean): Font {
    if (enabled) {
      return font.deriveFont(LIGATURES_ON)
    }
    return font
  }

  /**
   * Returns the font with the ligatures and the character variants of the editor settings.
   * When [characterVariants] is empty, the font gets only the ligatures.
   */
  @JvmStatic
  fun withFeatures(font: Font, useLigatures: Boolean, characterVariants: Set<String>): Font {
    val fontWithLigatures = withLigatures(font, useLigatures)
    if (characterVariants.isEmpty()) {
      return fontWithLigatures
    }
    return JBR.getFontExtensions().deriveFontWithFeatures(fontWithLigatures, *characterVariants.toTypedArray())
  }
}
