// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.html

import com.intellij.lang.documentation.DocumentationSettings
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.impl.EditorCssFontResolver
import com.intellij.ui.ColorUtil
import com.intellij.ui.scale.JBUIScale.scale
import com.intellij.util.containers.addAllIfNotNull
import org.jetbrains.annotations.ApiStatus.Internal
import java.awt.Color
import java.lang.Integer.toHexString
import javax.swing.UIManager

/**
 * Provides list of CSS rules for rendering code tags
 */
@Internal
@Suppress("UseJBColor")
object StyleSheetRulesProviderForCodeHighlighting {

  const val CODE_BLOCK_PREFIX = "<div class='styled-code'><pre style=\"padding: 0px; margin: 0px\">"
  const val CODE_BLOCK_SUFFIX = "</pre></div>"

  const val INLINE_CODE_PREFIX = "<code>"
  const val INLINE_CODE_SUFFIX = "</code>"

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

  @JvmStatic
  fun getRules(
    colorScheme: EditorColorsScheme,
    editorPaneBackgroundColor: Color,
    inlineCodeParentSelectors: List<String>,
    largeCodeFontSizeSelectors: List<String>,
    enableInlineCodeBackground: Boolean,
    enableCodeBlocksBackground: Boolean,
    codeBlockMargin: String
  ): List<String> {
    val result = mutableListOf<String>()

    // TODO: When removing `getMonospaceFontSizeCorrection` copy it's code here
    @Suppress("DEPRECATION", "removal")
    val definitionCodeFontSizePercent = DocumentationSettings.getMonospaceFontSizeCorrection(false)

    @Suppress("DEPRECATION", "removal")
    val contentCodeFontSizePercent = DocumentationSettings.getMonospaceFontSizeCorrection(true)

    result.addAllIfNotNull(
      "tt, code, pre, .pre { font-family:\"${EditorCssFontResolver.EDITOR_FONT_NAME_NO_LIGATURES_PLACEHOLDER}\"; font-size:$contentCodeFontSizePercent%; }",
    )
    if (largeCodeFontSizeSelectors.isNotEmpty()) {
      result.add("${largeCodeFontSizeSelectors.joinToString(", ")} { font-size: $definitionCodeFontSizePercent% }")
    }
    if (enableInlineCodeBackground) {
      val selectors = inlineCodeParentSelectors.asSequence().map { "$it code" }.joinToString(", ")
      result.add("$selectors { ${inlineCodeStyling.getCssStyle(editorPaneBackgroundColor, colorScheme)} }")
      result.add("$selectors { padding: ${scale(1)}px ${scale(4)}px; margin: ${scale(1)}px 0px; }")
    }
    if (enableCodeBlocksBackground) {
      val defaultBgColor = colorScheme.defaultBackground
      val blockCodeStyling = if (ColorUtil.getContrast(defaultBgColor, editorPaneBackgroundColor) < 1.1)
        blockCodeStyling.copy(
          suffix = ".EditorPane",
          defaultBackgroundColor = Color(0x5A5D6B),
          defaultBackgroundOpacity = 4,
        )
      else
        blockCodeStyling
      result.add("div.styled-code { ${blockCodeStyling.getCssStyle(editorPaneBackgroundColor, colorScheme)} }")
      result.add("div.styled-code { margin: $codeBlockMargin; padding: ${scale(6)}px ${scale(6)}px ${scale(6)}px ${scale(10)}px; }")
      result.add("div.styled-code pre { padding: 0px; margin: 0px }")
    }
    return result
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