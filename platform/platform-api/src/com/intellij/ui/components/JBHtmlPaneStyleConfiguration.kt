// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.components

import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.colors.TextAttributesKey
import org.jetbrains.annotations.ApiStatus.Experimental
import java.util.*

@Experimental
class JBHtmlPaneStyleConfiguration private constructor(builder: Builder) {
  val colorScheme: EditorColorsScheme = builder.colorScheme
  val editorInlineContext: Boolean = builder.editorInlineContext
  val inlineCodeParentSelectors: List<String> = builder.inlineCodeParentSelectors
  val largeCodeFontSizeSelectors: List<String> = builder.largeCodeFontSizeSelectors
  val enableInlineCodeBackground: Boolean = builder.enableInlineCodeBackground
  val enableCodeBlocksBackground: Boolean = builder.enableCodeBlocksBackground
  val useFontLigaturesInCode: Boolean = builder.useFontLigaturesInCode

  /** unscaled */
  val spaceBeforeParagraph: Int = builder.spaceBeforeParagraph

  /** unscaled */
  val spaceAfterParagraph: Int = builder.spaceAfterParagraph
  val elementStyleOverrides: ElementStyleOverrides? = builder.elementStyleOverrides

  constructor() : this(builder())

  constructor(configure: Builder.() -> Unit) : this(builder().also { configure(it) })

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
    && ElementKind.entries.all {
      colorScheme.getAttributes(it.colorSchemeKey, false) ==
        colorScheme2.getAttributes(it.colorSchemeKey, false)
    }

  override fun hashCode(): Int =
    Objects.hash(colorScheme.defaultBackground.rgb and 0xffffff,
                 colorScheme.defaultForeground.rgb and 0xffffff,
                 inlineCodeParentSelectors, largeCodeFontSizeSelectors,
                 enableInlineCodeBackground, enableCodeBlocksBackground,
                 useFontLigaturesInCode, spaceBeforeParagraph, spaceAfterParagraph)

  class ElementStyleOverrides(builder: Builder) {
    val elementKindThemePropertySuffix: String = builder.elementKindThemePropertySuffix?.takeUnless { it.isBlank() }
                                                 ?: throw IllegalStateException("elementKindThemePropertySuffix must not be null or blank")
    val overrides: Map<ElementKind, Collection<ElementProperty>> = builder.overrides.mapValues { it.value.toList() }

    override fun equals(other: Any?): Boolean =
      other is ElementStyleOverrides
      && other.elementKindThemePropertySuffix == elementKindThemePropertySuffix
      && other.overrides == overrides

    override fun hashCode(): Int =
      Objects.hash(elementKindThemePropertySuffix, overrides)

    class Builder {

      var elementKindThemePropertySuffix: String? = null

      internal val overrides: MutableMap<ElementKind, MutableCollection<ElementProperty>> = mutableMapOf()

      fun overrideThemeProperties(elementKind: ElementKind, vararg properties: ElementProperty): Builder =
        apply { overrides.getOrPut(elementKind) { mutableListOf() } += properties }

      fun elementKindThemePropertySuffix(elementKindThemePropertySuffix: String): Builder =
        apply { this.elementKindThemePropertySuffix = elementKindThemePropertySuffix }

      fun build(): ElementStyleOverrides =
        ElementStyleOverrides(this)

    }

    companion object {

      @JvmStatic
      fun builder(): Builder =
        Builder()
    }
  }

  enum class ElementKind(val id: String, val colorSchemeKey: TextAttributesKey) {
    CodeInline("Code.Inline", DefaultLanguageHighlighterColors.DOC_CODE_INLINE),
    CodeBlock("Code.Block", DefaultLanguageHighlighterColors.DOC_CODE_BLOCK),
    Shortcut("Shortcut", DefaultLanguageHighlighterColors.DOC_TIPS_SHORTCUT),
  }

  enum class ElementProperty(val id: String) {
    BackgroundColor("backgroundColor"),
    ForegroundColor("foregroundColor"),
    BorderColor("borderColor"),
    BackgroundOpacity("backgroundOpacity"),
    BorderWidth("borderWidth"),
    BorderRadius("borderRadius"),
  }

  class Builder {
    var colorScheme: EditorColorsScheme = EditorColorsManager.getInstance().globalScheme
    var editorInlineContext: Boolean = false
    var inlineCodeParentSelectors: List<String> = listOf("")
    var largeCodeFontSizeSelectors: List<String> = emptyList()
    var enableInlineCodeBackground: Boolean = true
    var enableCodeBlocksBackground: Boolean = true
    var useFontLigaturesInCode: Boolean = false

    /** unscaled */
    var spaceBeforeParagraph: Int = defaultSpaceBeforeParagraph

    /** Unscaled */
    var spaceAfterParagraph: Int = defaultSpaceAfterParagraph
    var elementStyleOverrides: ElementStyleOverrides? = null

    fun build(): JBHtmlPaneStyleConfiguration = JBHtmlPaneStyleConfiguration(this)

    fun colorScheme(colorScheme: EditorColorsScheme): Builder =
      apply { this.colorScheme = colorScheme }

    fun editorInlineContext(editorInlineContext: Boolean): Builder =
      apply { this.editorInlineContext = editorInlineContext }

    fun inlineCodeParentSelectors(inlineCodeParentSelectors: List<String>): Builder =
      apply { this.inlineCodeParentSelectors = inlineCodeParentSelectors }

    fun largeCodeFontSizeSelectors(largeCodeFontSizeSelectors: List<String>): Builder =
      apply { this.largeCodeFontSizeSelectors = largeCodeFontSizeSelectors }

    fun enableInlineCodeBackground(enableInlineCodeBackground: Boolean): Builder =
      apply { this.enableInlineCodeBackground = enableInlineCodeBackground }

    fun enableCodeBlocksBackground(enableCodeBlocksBackground: Boolean): Builder =
      apply { this.enableCodeBlocksBackground = enableCodeBlocksBackground }

    fun useFontLigaturesInCode(useFontLigaturesInCode: Boolean): Builder =
      apply { this.useFontLigaturesInCode = useFontLigaturesInCode }

    fun spaceBeforeParagraph(spaceBeforeParagraph: Int): Builder =
      apply { this.spaceBeforeParagraph = spaceBeforeParagraph }

    fun spaceAfterParagraph(spaceAfterParagraph: Int): Builder =
      apply { this.spaceAfterParagraph = spaceAfterParagraph }

    fun overrideElementStyle(elementStyleOverrides: ElementStyleOverrides): Builder =
      apply { this.elementStyleOverrides = elementStyleOverrides }

    fun overrideElementStyle(configuration: ElementStyleOverrides.Builder.() -> Unit): Builder =
      apply { this.elementStyleOverrides = ElementStyleOverrides.builder().also(configuration).build() }

  }

  companion object {
    @JvmStatic
    val defaultSpaceBeforeParagraph: Int get() = 4

    @JvmStatic
    val defaultSpaceAfterParagraph: Int get() = 4

    @JvmStatic
    val editorColorClassPrefix: String = "editor-color-"

    @JvmStatic
    fun builder(): Builder =
      Builder()

  }

}