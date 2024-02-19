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
import kotlin.math.abs

/**
 * Provides list of CSS rules for rendering code tags
 */
@Internal
object StyleSheetRulesProviderForCodeHighlighting {

  const val CODE_BLOCK_PREFIX = "<div class='styled-code'><pre style=\"padding: 0px; margin: 0px\">"
  const val CODE_BLOCK_SUFFIX = "</pre></div>"

  const val INLINE_CODE_PREFIX = "<code>"
  const val INLINE_CODE_SUFFIX = "</code>"

  @JvmStatic
  @JvmOverloads
  fun getRules(
    colorScheme: EditorColorsScheme,
    editorPaneBackgroundColor: Color,
    inlineCodeParentSelectors: List<String>,
    largeCodeFontSizeSelectors: List<String>,
    enableInlineCodeBackground: Boolean,
    enableCodeBlocksBackground: Boolean,
    codeBlockMargin: String,
    preferredBackgroundColor: Color? = null
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

    if (!enableCodeBlocksBackground && !enableInlineCodeBackground)
      return result

    val codeBg = ColorUtil.toHtmlColor(editorPaneBackgroundColor.let {
      val luminance = ColorUtil.getLuminance(it).toFloat()
      if (preferredBackgroundColor != null
          && luminance > 0.5
          && abs(ColorUtil.getLuminance(preferredBackgroundColor) - luminance) > 0.1) {
        preferredBackgroundColor
      }
      else {
        val change = when {
          // In case of a very dark theme, make background lighter
          luminance < 0.028f -> 1.55f - luminance * 10f
          // In any other case make background darker
          luminance < 0.1 -> 0.89f
          luminance < 0.15 -> 0.85f
          else -> 0.96f
        }
        ColorUtil.hackBrightness(it, 1, change)
      }
    })
    val codeFg = ColorUtil.toHtmlColor(colorScheme.defaultForeground)

    val codeColorStyle = "{ background-color: $codeBg; color: $codeFg; }"

    if (enableInlineCodeBackground) {
      val selectors = inlineCodeParentSelectors.asSequence().map { "$it code" }.joinToString(", ")
      result.add("$selectors $codeColorStyle")
      // 'caption-side' is a hack to support 'border-radius'.
      // See also: com.intellij.util.ui.html.InlineViewEx
      result.add("$selectors { padding: ${scale(1)}px ${scale(4)}px; margin: ${scale(1)}px 0px; caption-side: ${scale(10)}px; }")
    }
    if (enableCodeBlocksBackground) {
      result.add("div.styled-code $codeColorStyle")
      result.add("div.styled-code {  margin: $codeBlockMargin; padding: ${scale(6)}px ${scale(6)}px ${scale(6)}px ${scale(10)}px; }")
      result.add("div.styled-code pre { padding: 0px; margin: 0px }")
    }
    return result
  }

}