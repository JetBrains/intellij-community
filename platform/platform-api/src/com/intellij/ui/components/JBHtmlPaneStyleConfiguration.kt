// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.components

import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.colors.TextAttributesKey
import java.util.*

data class JBHtmlPaneStyleConfiguration(
  val colorScheme: EditorColorsScheme = EditorColorsManager.getInstance().globalScheme,
  val editorInlineContext: Boolean = false,
  val inlineCodeParentSelectors: List<String> = listOf(""),
  val largeCodeFontSizeSelectors: List<String> = emptyList(),
  val enableInlineCodeBackground: Boolean = true,
  val enableCodeBlocksBackground: Boolean = true,
  val useFontLigaturesInCode: Boolean = false,
  /** Unscaled */
  val spaceBeforeParagraph: Int = Companion.defaultSpaceBeforeParagraph,
  /** Unscaled */
  val spaceAfterParagraph: Int = Companion.defaultSpaceAfterParagraph,
  val controlStyleOverrides: ControlStyleOverrides? = null,
) {
  override fun equals(other: Any?): Boolean =
    other is JBHtmlPaneStyleConfiguration
    && colorSchemesEqual(colorScheme, other.colorScheme)
    && inlineCodeParentSelectors == other.inlineCodeParentSelectors
    && largeCodeFontSizeSelectors == other.largeCodeFontSizeSelectors
    && enableInlineCodeBackground == other.enableInlineCodeBackground
    && enableCodeBlocksBackground == other.enableCodeBlocksBackground
    && useFontLigaturesInCode == other.useFontLigaturesInCode
    && spaceBeforeParagraph == other.spaceBeforeParagraph
    && spaceAfterParagraph == other.spaceAfterParagraph

  private fun colorSchemesEqual(colorScheme: EditorColorsScheme, colorScheme2: EditorColorsScheme): Boolean =
    // Update here when more colors are used from the colorScheme
    colorScheme.defaultBackground.rgb == colorScheme2.defaultBackground.rgb
    && colorScheme.defaultForeground.rgb == colorScheme2.defaultForeground.rgb
    && ControlKind.entries.all {
      colorScheme.getAttributes(it.colorSchemeKey, false) ==
        colorScheme2.getAttributes(it.colorSchemeKey, false)
    }


  override fun hashCode(): Int =
    Objects.hash(colorScheme.defaultBackground.rgb and 0xffffff,
                 colorScheme.defaultForeground.rgb and 0xffffff,
                 inlineCodeParentSelectors, largeCodeFontSizeSelectors,
                 enableInlineCodeBackground, enableCodeBlocksBackground,
                 useFontLigaturesInCode, spaceBeforeParagraph, spaceAfterParagraph)

  data class ControlStyleOverrides(
    val controlKindSuffix: String,
    val overrides: Map<ControlKind, Collection<ControlProperty>>
  )

  enum class ControlKind(val id: String, val colorSchemeKey: TextAttributesKey) {
    CodeInline("Code.Inline", DefaultLanguageHighlighterColors.DOC_CODE_INLINE),
    CodeBlock("Code.Block", DefaultLanguageHighlighterColors.DOC_CODE_BLOCK),
    Shortcut("Shortcut", DefaultLanguageHighlighterColors.DOC_TIPS_SHORTCUT),
  }

  enum class ControlProperty(val id: String) {
    BackgroundColor("backgroundColor"),
    ForegroundColor("foregroundColor"),
    BorderColor("borderColor"),
    BackgroundOpacity("backgroundOpacity"),
    BorderWidth("borderWidth"),
    BorderRadius("borderRadius"),
  }

  companion object {
    @JvmStatic
    val defaultSpaceBeforeParagraph: Int get() = 4

    @JvmStatic
    val defaultSpaceAfterParagraph: Int get() = 4
  }

}