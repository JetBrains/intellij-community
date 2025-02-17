// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.components

import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.ui.components.JBHtmlPaneStyleConfiguration.Companion.defaultSpaceAfterParagraph
import com.intellij.ui.components.JBHtmlPaneStyleConfiguration.Companion.defaultSpaceBeforeParagraph
import com.intellij.ui.scale.JBUIScale
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.ApiStatus.Experimental
import java.util.*

@Experimental
/**
 * Provides a set of style configuration options for the creation of
 * default CSS rules for the [JBHtmlPane].
 *
 * Use [JBHtmlPaneStyleConfiguration] constructor for default options, or
 * [JBHtmlPaneStyleConfiguration.builder] if you want to customize anything.
 *
 * Builder supports both traditional call chaining style
 * ```java
 * JBHtmlPaneStyleConfiguration.builder()
 *   .colorScheme(colorScheme)
 *   .inlineCodeParentSelectors("." + CLASS_CONTENT)
 *   .enableCodeBlocksBackground(DocumentationSettings.isCodeBackgroundEnabled())
 *   .build()
 * ```
 * and kotlin lambda style:
 * ```kotlin
 * JBHtmlPaneStyleConfiguration {
 *   this.colorScheme = colorScheme
 *   inlineCodeParentSelectors(".$CLASS_CONTENT")
 *   enableInlineCodeBackground = DocumentationSettings.isCodeBackgroundEnabled()
 *   if (editorInlineContext)
 *     overrideElementStyle {
 *       elementKindThemePropertySuffix = "EditorPane"
 *       overrideThemeProperties(ElementKind.CodeBlock, ElementProperty.BackgroundColor)
 *     }
 * }
 * ```
 */
class JBHtmlPaneStyleConfiguration private constructor(builder: Builder) {
  @ApiStatus.Internal
  val colorSchemeProvider: () -> EditorColorsScheme = builder.colorSchemeProvider

  @ApiStatus.Internal
  val editorInlineContext: Boolean = builder.editorInlineContext

  @ApiStatus.Internal
  val inlineCodeParentSelectors: List<String> = builder.inlineCodeParentSelectors.toList().ifEmpty { listOf("") }

  @ApiStatus.Internal
  val largeCodeFontSizeSelectors: List<String> = builder.largeCodeFontSizeSelectors.toList()

  @ApiStatus.Internal
  val enableInlineCodeBackground: Boolean = builder.enableInlineCodeBackground

  @ApiStatus.Internal
  val enableCodeBlocksBackground: Boolean = builder.enableCodeBlocksBackground

  @ApiStatus.Internal
  val useFontLigaturesInCode: Boolean = builder.useFontLigaturesInCode

  /** unscaled */
  @ApiStatus.Internal
  val spaceBeforeParagraph: Int = builder.spaceBeforeParagraph

  /** unscaled */
  @ApiStatus.Internal
  val spaceAfterParagraph: Int = builder.spaceAfterParagraph

  @ApiStatus.Internal
  val elementStyleOverrides: ElementStyleOverrides? = builder.elementStyleOverrides

  constructor() : this(builder())

  constructor(configure: Builder.() -> Unit) : this(builder().also { configure(it) })

  /**
   * Allows overriding default theme styling for [JBHtmlPane] elements, like code
   * blocks, code fragments or shortcuts.
   *
   * For instance, if you want to have a different styling for [ElementKind.Shortcut]
   * in your `Special` dialog, you need to provide a suffix name and a set of overrides:
   * ```
   * elementKindThemePropertySuffix = "SpecialDialog"
   * overrideThemeProperties(ElementKind.Shortcut, ElementProperty.ForegroundColor)
   * ```
   * Next, you need to register a key in IJ Theme for each overridden property,
   * e.g.: `Shortcut.SpecialDialog.foregroundColor` and provide the
   * [new values for themes](https://plugins.jetbrains.com/docs/intellij/themes-customize.html)
   */
  class ElementStyleOverrides(builder: Builder) {

    @ApiStatus.Internal
    val elementKindThemePropertySuffix: String = builder.elementKindThemePropertySuffix?.takeUnless { it.isBlank() }
                                                 ?: throw IllegalStateException("elementKindThemePropertySuffix must not be null or blank")

    @ApiStatus.Internal
    val overrides: Map<ElementKind, Collection<ElementProperty>> = builder.overrides.mapValues { it.value.toList() }

    override fun equals(other: Any?): Boolean =
      other is ElementStyleOverrides
      && other.elementKindThemePropertySuffix == elementKindThemePropertySuffix
      && other.overrides == overrides

    override fun hashCode(): Int =
      Objects.hash(elementKindThemePropertySuffix, overrides)

    class Builder {

      /**
       * Provide a suffix for an IJ Theme key for the overridden element settings.
       *
       * @see ElementStyleOverrides
       */
      var elementKindThemePropertySuffix: String? = null

      internal val overrides: MutableMap<ElementKind, MutableCollection<ElementProperty>> = mutableMapOf()

      /**
       * Register theme property overrides for a particular [elementKind].
       *
       * @see ElementStyleOverrides
       */
      fun overrideThemeProperties(elementKind: ElementKind, vararg properties: ElementProperty): Builder =
        apply { overrides.getOrPut(elementKind) { mutableListOf() }.addAll(properties) }

      /**
       * Provide a suffix for an IJ Theme key for the overridden element settings.
       *
       * @see ElementStyleOverrides
       */
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
    /**
     * Provide an editor color scheme to be used to determine colors of the elements
     * and syntax highlighting.
     */
    @get:ApiStatus.ScheduledForRemoval()
    @get:Deprecated("Use colorSchemeProvider instead to properly react for global scheme changes", ReplaceWith("colorSchemeProvider = { colorScheme }"))
    @set:ApiStatus.ScheduledForRemoval()
    @set:Deprecated("Use colorSchemeProvider instead to properly react for global scheme changes", ReplaceWith("colorSchemeProvider = { colorScheme }"))
    var colorScheme: EditorColorsScheme = EditorColorsManager.getInstance().globalScheme
      set(value) {
        field = value
        colorSchemeProvider = { value }
      }

    /**
     * Provide an editor color scheme to be used to determine colors of the elements
     * and syntax highlighting.
     */
    var colorSchemeProvider: () -> EditorColorsScheme = { EditorColorsManager.getInstance().globalScheme }

    /**
     * Whether the [JBHtmlPane] is placed inline within an editor or an equivalent control.
     *
     * Default: `false`
     */
    var editorInlineContext: Boolean = false

    /**
     * Selectors for elements, within which `<code>` elements should be rendered with a background.
     *
     * Default: `listOf("")`
     */
    var inlineCodeParentSelectors: MutableList<String> = mutableListOf()

    /**
     * Selectors for elements where the code font should have regular size.
     * Usually, the code font size is adjusted to match the size of the text font.
     *
     * Default: `emptyList()`
     */
    var largeCodeFontSizeSelectors: MutableList<String> = mutableListOf()

    /**
     * Whether `<code>` elements should be rendered with a background.
     *
     * Default: `true`
     */
    var enableInlineCodeBackground: Boolean = true

    /**
     * Whether `<pre><code>` and `<blockquote><pre>` elements should be rendered with a background.
     *
     * Default: `true`
     */
    var enableCodeBlocksBackground: Boolean = true

    /**
     * Whether code fragments should use font ligatures.
     * This affects performance, and for larger HTMLs it can cause visible performance degradation.
     *
     * Default: `false`
     */
    var useFontLigaturesInCode: Boolean = false

    /**
     * Override the default CSS spacing before elements.
     * The value **should not** be scaled using [JBUIScale.scale]
     *
     * Default: [defaultSpaceBeforeParagraph] = `4`
     */
    var spaceBeforeParagraph: Int = defaultSpaceBeforeParagraph

    /**
     * Override the default CSS spacing after elements.
     * The value **should not** be scaled using [JBUIScale.scale]
     *
     * Default: [defaultSpaceAfterParagraph] = `4`
     */
    var spaceAfterParagraph: Int = defaultSpaceAfterParagraph
    var elementStyleOverrides: ElementStyleOverrides? = null

    fun build(): JBHtmlPaneStyleConfiguration = JBHtmlPaneStyleConfiguration(this)

    /**
     * Provide an editor color scheme to be used to determine colors of the elements
     * and syntax highlighting.
     */
    @Deprecated("Use colorSchemeProvider instead to properly react for global scheme changes", ReplaceWith("colorSchemeProvider { colorScheme }"))
    fun colorScheme(colorScheme: EditorColorsScheme): Builder =
      apply { this.colorSchemeProvider = { colorScheme } }

    /**
     * Provide an editor color scheme to be used to determine colors of the elements
     * and syntax highlighting.
     */
    fun colorSchemeProvider(colorSchemeProvider: () -> EditorColorsScheme): Builder =
      apply { this.colorSchemeProvider = colorSchemeProvider }

    /**
     * Whether the [JBHtmlPane] is placed inline within an editor or an equivalent control.
     *
     * Default: `false`
     */
    fun editorInlineContext(editorInlineContext: Boolean): Builder =
      apply { this.editorInlineContext = editorInlineContext }

    /**
     * Selectors for elements, within which `<code>` elements should be rendered with a background.
     *
     * Default: `listOf("")`
     */
    fun inlineCodeParentSelectors(inlineCodeParentSelectors: List<String>): Builder =
      apply { this.inlineCodeParentSelectors.addAll(inlineCodeParentSelectors) }

    /**
     * Selectors for elements, within which `<code>` elements should be rendered with a background.
     *
     * Default: `listOf("")`
     */
    fun inlineCodeParentSelectors(vararg inlineCodeParentSelectors: String): Builder =
      apply { this.inlineCodeParentSelectors.addAll(inlineCodeParentSelectors) }

    /**
     * Selectors for elements where the code font should have regular size.
     * Usually, the code font size is adjusted to match the size of the text font.
     *
     * Default: `emptyList()`
     */
    fun largeCodeFontSizeSelectors(largeCodeFontSizeSelectors: List<String>): Builder =
      apply { this.largeCodeFontSizeSelectors.addAll(largeCodeFontSizeSelectors) }

    /**
     * Selectors for elements where the code font should have regular size.
     * Usually, the code font size is adjusted to match the size of the text font.
     *
     * Default: `emptyList()`
     */
    fun largeCodeFontSizeSelectors(vararg largeCodeFontSizeSelectors: String): Builder =
      apply { this.largeCodeFontSizeSelectors.addAll(largeCodeFontSizeSelectors) }

    /**
     * Whether `<code>` elements should be rendered with a background.
     *
     * Default: `true`
     */
    fun enableInlineCodeBackground(enableInlineCodeBackground: Boolean): Builder =
      apply { this.enableInlineCodeBackground = enableInlineCodeBackground }

    /**
     * Whether `<pre><code>` and `<blockquote><pre>` elements should be rendered with a background.
     *
     * Default: `true`
     */
    fun enableCodeBlocksBackground(enableCodeBlocksBackground: Boolean): Builder =
      apply { this.enableCodeBlocksBackground = enableCodeBlocksBackground }

    /**
     * Whether code fragments should use font ligatures.
     * This affects performance, and for larger HTMLs it can cause visible performance degradation.
     *
     * Default: `false`
     */
    fun useFontLigaturesInCode(useFontLigaturesInCode: Boolean): Builder =
      apply { this.useFontLigaturesInCode = useFontLigaturesInCode }

    /**
     * Override the default CSS spacing before elements.
     * The value **should not** be scaled using [JBUIScale.scale]
     *
     * Default: [defaultSpaceBeforeParagraph] = `4`
     */
    fun spaceBeforeParagraph(spaceBeforeParagraph: Int): Builder =
      apply { this.spaceBeforeParagraph = spaceBeforeParagraph }

    /**
     * Override the default CSS spacing after elements.
     * The value **should not** be scaled using [JBUIScale.scale]
     *
     * Default: [defaultSpaceAfterParagraph] = `4`
     */
    fun spaceAfterParagraph(spaceAfterParagraph: Int): Builder =
      apply { this.spaceAfterParagraph = spaceAfterParagraph }

    /**
     * Override default theme settings for HTML controls.
     *
     * Use [ElementStyleOverrides.builder] to create the override configuration
     */
    fun overrideElementStyle(elementStyleOverrides: ElementStyleOverrides): Builder =
      apply { this.elementStyleOverrides = elementStyleOverrides }

    /**
     * Override default theme settings for HTML controls.
     *
     * Create an override configuration within a Kotlin configuration lambda.
     */
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