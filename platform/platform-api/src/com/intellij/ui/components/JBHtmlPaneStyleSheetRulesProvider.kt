// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.components

import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.LoadingCache
import com.intellij.lang.documentation.DocumentationMarkup.CLASS_CENTERED
import com.intellij.lang.documentation.DocumentationSettings
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.impl.EditorCssFontResolver.EDITOR_FONT_NAME_NO_LIGATURES_PLACEHOLDER
import com.intellij.openapi.editor.impl.EditorCssFontResolver.EDITOR_FONT_NAME_PLACEHOLDER
import com.intellij.ui.ColorUtil
import com.intellij.ui.scale.JBUIScale.scale
import com.intellij.util.containers.addAllIfNotNull
import com.intellij.util.ui.StartupUiUtil
import com.intellij.util.ui.StyleSheetUtil
import org.intellij.lang.annotations.Language
import org.jetbrains.annotations.ApiStatus.Internal
import java.awt.Color
import java.lang.Integer.toHexString
import java.util.*
import javax.swing.UIManager
import javax.swing.text.html.StyleSheet

/**
 * Provides list of default CSS rules for JBHtmlPane
 */
@Internal
@Suppress("UseJBColor", "CssInvalidHtmlTagReference", "CssInvalidPropertyValue")
object JBHtmlPaneStyleSheetRulesProvider {

  @JvmStatic
  fun getStyleSheet(paneBackgroundColor: Color, configuration: Configuration): StyleSheet =
    styleSheetCache.get(Pair(paneBackgroundColor.rgb and 0xffffff, configuration))

  data class Configuration(
    val colorScheme: EditorColorsScheme = EditorColorsManager.getInstance().globalScheme,
    val inlineCodeParentSelectors: List<String> = listOf(""),
    val largeCodeFontSizeSelectors: List<String> = emptyList(),
    val enableInlineCodeBackground: Boolean = true,
    val enableCodeBlocksBackground: Boolean = true,
    val useFontLigaturesInCode: Boolean = false,
    /** Unscaled */
    val spaceBeforeParagraph: Int = JBHtmlPaneStyleSheetRulesProvider.spaceBeforeParagraph,
    /** Unscaled */
    val spaceAfterParagraph: Int = JBHtmlPaneStyleSheetRulesProvider.spaceAfterParagraph,
  ) {
    override fun equals(other: Any?): Boolean =
      other is Configuration
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

    override fun hashCode(): Int =
      Objects.hash(colorScheme.defaultBackground.rgb and 0xffffff,
                   colorScheme.defaultForeground.rgb and 0xffffff,
                   inlineCodeParentSelectors, largeCodeFontSizeSelectors,
                   enableInlineCodeBackground, enableCodeBlocksBackground,
                   useFontLigaturesInCode, spaceBeforeParagraph, spaceAfterParagraph)
  }

  const val CODE_BLOCK_PREFIX: String = "<div class='styled-code'><pre style=\"padding: 0px; margin: 0px\">"
  const val CODE_BLOCK_SUFFIX: String = "</pre></div>"

  @JvmStatic
  val spaceBeforeParagraph: Int get() = 4

  @JvmStatic
  val spaceAfterParagraph: Int get() = 4

  private val inlineCodeStyling = ControlColorStyleBuilder(
    "Code.Inline",
    defaultBackgroundColor = Color(0x5A5D6B),
    defaultBackgroundOpacity = 10,
    defaultBorderRadius = 10,
  )

  private val blockCodeStyling = ControlColorStyleBuilder(
    "Code.Block",
    defaultBorderColor = Color(0xEBECF0),
    defaultBorderRadius = 10,
    defaultBorderWidth = 1,
  )

  private val styleSheetCache: LoadingCache<Pair<Int, Configuration>, StyleSheet> = Caffeine.newBuilder()
    .maximumSize(20)
    .build { (bgColor, configuration) ->
      StyleSheetUtil.loadStyleSheet(
        getDefaultFormattingStyles(configuration) + "\n" + getCodeRules(Color(bgColor), configuration))
    }

  private fun getDefaultFormattingStyles(configuration: Configuration): String {
    val fontSize = StartupUiUtil.labelFont.size
    val spacingBefore = scale(configuration.spaceBeforeParagraph)
    val spacingAfter = scale(configuration.spaceAfterParagraph)
    val paragraphSpacing = """padding: ${spacingBefore}px 0 ${spacingAfter}px 0"""

    @Language("CSS")
    val styles = """
      h6 { font-size: ${fontSize + 1}}
      h5 { font-size: ${fontSize + 2}}
      h4 { font-size: ${fontSize + 3}}
      h3 { font-size: ${fontSize + 4}}
      h2 { font-size: ${fontSize + 6}}
      h1 { font-size: ${fontSize + 8}}
      h1, h2, h3, h4, h5, h6 {margin: 0 0 0 0; ${paragraphSpacing}; }
      p { margin: 0 0 0 0; ${paragraphSpacing}; line-height: 125%; }
      ul { margin: 0 0 0 ${scale(10)}px; ${paragraphSpacing};}
      ol { margin: 0 0 0 ${scale(20)}px; ${paragraphSpacing};}
      li { padding: ${scale(1)}px 0 ${scale(2)}px 0; }
      li p { padding-top: 0; padding-bottom: 0; }
      th { text-align: left; }
      tr, table { margin: 0 0 0 0; padding: 0 0 0 0; }
      td { margin: 0 0 0 0; padding: ${spacingBefore}px ${spacingBefore + spacingAfter}px ${spacingAfter}px 0; }
      td p { padding-top: 0; padding-bottom: 0; }
      td pre { padding: ${scale(1)}px 0 0 0; margin: 0 0 0 0 }
      .$CLASS_CENTERED { text-align: center}
    """.trimIndent()
    return styles
  }

  @JvmStatic
  private fun getCodeRules(
    paneBackgroundColor: Color,
    configuration: Configuration,
  ): String {
    val result = mutableListOf<String>()
    val spacingBefore = scale(configuration.spaceBeforeParagraph)
    val spacingAfter = scale(configuration.spaceAfterParagraph)

    // TODO: When removing `getMonospaceFontSizeCorrection` copy it's code here
    @Suppress("DEPRECATION", "removal")
    val definitionCodeFontSizePercent = DocumentationSettings.getMonospaceFontSizeCorrection(false)

    @Suppress("DEPRECATION", "removal")
    val contentCodeFontSizePercent = DocumentationSettings.getMonospaceFontSizeCorrection(true)

    val fontName = if (configuration.useFontLigaturesInCode) EDITOR_FONT_NAME_PLACEHOLDER else EDITOR_FONT_NAME_NO_LIGATURES_PLACEHOLDER
    result.addAllIfNotNull(
      "tt, code, pre, .pre { font-family:\"$fontName\"; font-size:$contentCodeFontSizePercent%; }",
    )
    if (configuration.largeCodeFontSizeSelectors.isNotEmpty()) {
      result.add("${configuration.largeCodeFontSizeSelectors.joinToString(", ")} { font-size: $definitionCodeFontSizePercent% }")
    }
    if (configuration.enableInlineCodeBackground) {
      val selectors = configuration.inlineCodeParentSelectors.asSequence().map { "$it code" }.joinToString(", ")
      result.add("$selectors { ${inlineCodeStyling.getCssStyle(paneBackgroundColor, configuration.colorScheme)} }")
      result.add("$selectors { padding: ${scale(1)}px ${scale(4)}px; margin: ${scale(1)}px 0px; }")
    }
    if (configuration.enableCodeBlocksBackground) {
      val defaultBgColor = configuration.colorScheme.defaultBackground
      val blockCodeStyling = if (ColorUtil.getContrast(defaultBgColor, paneBackgroundColor) < 1.1)
        blockCodeStyling.copy(
          suffix = ".EditorPane",
          defaultBackgroundColor = Color(0x5A5D6B),
          defaultBackgroundOpacity = 4,
        )
      else
        blockCodeStyling
      result.add("div.styled-code { ${blockCodeStyling.getCssStyle(paneBackgroundColor, configuration.colorScheme)} }")
      result.add("div.styled-code { margin: ${spacingBefore}px 0 ${spacingAfter}px 0; padding: ${scale(10)}px ${scale(13)}px ${scale(10)}px ${scale(13)}px; }")
      result.add("div.styled-code pre { padding: 0px; margin: 0px; line-height: 120%; }")
    }
    return result.joinToString("\n");
  }

  private data class ControlColorStyleBuilder(
    val id: String,
    val suffix: String = "",
    val defaultBackgroundColor: Color? = null,
    val defaultBackgroundOpacity: Int = 100,
    val defaultForegroundColor: Color? = null,
    val defaultBorderColor: Color? = null,
    val defaultBorderWidth: Int = 0,
    val defaultBorderRadius: Int = 0,
  ) {

    private val backgroundColor: Color? get() = UIManager.getColor("$id$suffix.backgroundColor")

    private val foregroundColor: Color? get() = UIManager.getColor("$id.foregroundColor")

    private val borderColor: Color? get() = UIManager.getColor("$id$suffix.borderColor")

    private val backgroundOpacity: Int? get() = UIManager.get("$id$suffix.backgroundOpacity") as? Int

    private val borderWidth: Int? get() = UIManager.get("$id.borderWidth") as? Int

    private val borderRadius: Int? get() = UIManager.get("$id.borderRadius") as? Int

    fun getCssStyle(editorPaneBackgroundColor: Color, editorColorsScheme: EditorColorsScheme): String {
      val result = StringBuilder()

      result.append(backgroundColor, defaultBackgroundColor, editorColorsScheme.defaultBackground) {
        val opacity = choose(backgroundOpacity, defaultBackgroundOpacity) ?: 100
        val background = mixColors(editorPaneBackgroundColor, it, opacity)
        "background-color: #${toHtmlColor(background)};"
      }

      result.append(foregroundColor, defaultForegroundColor, editorColorsScheme.defaultForeground) {
        "color: #${toHtmlColor(it)};"
      }

      result.append(borderColor, defaultBorderColor) {
        "border-color: #${toHtmlColor(it)};"
      }

      result.append(borderWidth, defaultBorderWidth) {
        "border-width: ${scale(it)}px;"
      }

      // 'caption-side' is a hack to support 'border-radius'.
      // See also: com.intellij.util.ui.html.InlineViewEx
      result.append(borderRadius, defaultBorderRadius) {
        "caption-side: ${scale(it)}px;"
      }

      return result.toString()
    }

    fun <T : Any> choose(themeVersion: T?, defaultVersion: T?, editorVersion: T? = null): T? =
      themeVersion ?: defaultVersion ?: editorVersion

    fun <T : Any> StringBuilder.append(themeVersion: T?, defaultVersion: T?, editorVersion: T? = null, mapper: (T) -> String) {
      choose(themeVersion, defaultVersion, editorVersion)?.let(mapper)?.let { this.append(it) }
    }

    fun toHtmlColor(color: Color): String =
      toHexString(color.rgb and 0xFFFFFF)

    fun mixColors(c1: Color, c2: Color, opacity2: Int): Color {
      if (opacity2 >= 100) return c2
      if (opacity2 <= 0) return c1
      return Color(
        ((100 - opacity2) * c1.red + opacity2 * c2.red) / 100,
        ((100 - opacity2) * c1.green + opacity2 * c2.green) / 100,
        ((100 - opacity2) * c1.blue + opacity2 * c2.blue) / 100
      )
    }

  }

}