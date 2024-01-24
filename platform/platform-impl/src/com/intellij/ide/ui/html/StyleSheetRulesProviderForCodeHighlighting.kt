// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.html

import com.intellij.lang.documentation.DocumentationSettings
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.impl.EditorCssFontResolver
import com.intellij.ui.ColorUtil
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
      "pre {white-space: pre-wrap}",  // supported by JetBrains Runtime
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
      } else {
        val change = if (luminance < 0.028f)
        // In case of a very dark theme, make background lighter
          1.55f - luminance * 10f
        else if (luminance < 0.15)
        // In any other case make background darker
          0.85f
        else
          0.96f
        ColorUtil.hackBrightness(it, 1, change)
      }
    })
    val codeFg = ColorUtil.toHtmlColor(colorScheme.defaultForeground)

    val codeColorStyle = "{ background-color: $codeBg; color: $codeFg; }"

    if (enableInlineCodeBackground) {
      val selectors = inlineCodeParentSelectors.asSequence().map { "$it code" }.joinToString(", ")
      result.add("$selectors $codeColorStyle")
      result.add("$selectors { padding: 1px 4px; margin: 1px 0px; caption-side: 10px; }")
    }
    if (enableCodeBlocksBackground) {
      result.add("div.styled-code $codeColorStyle")
      result.add("div.styled-code {  margin: 5px 0px 5px 10px; padding: 6px; }")
      result.add("div.styled-code pre { padding: 0px; margin: 0px }")
    }
    return result
  }

}