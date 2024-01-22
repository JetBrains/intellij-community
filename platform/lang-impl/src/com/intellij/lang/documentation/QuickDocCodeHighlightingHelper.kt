// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.documentation

import com.intellij.lang.Language
import com.intellij.lang.documentation.DocumentationSettings.InlineCodeHighlightingMode
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.impl.EditorCssFontResolver.EDITOR_FONT_NAME_NO_LIGATURES_PLACEHOLDER
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.editor.richcopy.HtmlSyntaxInfoUtil
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.DefaultProjectFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.ColorUtil
import com.intellij.util.containers.addAllIfNotNull
import com.intellij.xml.util.XmlStringUtil
import java.awt.Color

object QuickDocCodeHighlightingHelper {

  const val CODE_BLOCK_PREFIX = "<div class='styled-code'><pre style=\"padding: 0px; margin: 0px\">"
  const val CODE_BLOCK_SUFFIX = "</pre></div>"

  const val INLINE_CODE_PREFIX = "<code>"
  const val INLINE_CODE_SUFFIX = "</code>"

  @JvmStatic
  fun getStyledCodeBlock(code: @NlsSafe CharSequence, language: Language?, project: Project? = null): @NlsSafe String =
    StringBuilder().apply { appendStyledCodeBlock(code, language, project) }.toString()

  @JvmStatic
  fun StringBuilder.appendStyledCodeBlock(code: @NlsSafe CharSequence,
                                          language: Language?,
                                          project: Project? = null): @NlsSafe StringBuilder =
    append(CODE_BLOCK_PREFIX)
      .appendHighlightedCode(DocumentationSettings.isHighlightingOfCodeBlocksEnabled(), code, language, project, true)
      .append(CODE_BLOCK_SUFFIX)

  @JvmStatic
  fun removeSurroundingStyledCodeBlock(string: String): String =
    string.trim().removeSurrounding(CODE_BLOCK_PREFIX, CODE_BLOCK_SUFFIX)

  @JvmStatic
  @JvmOverloads
  fun getStyledInlineCode(@NlsSafe code: String, language: Language? = null, project: Project? = null): @NlsSafe String =
    StringBuilder().apply { appendStyledInlineCode(code, language, project) }.toString()

  @JvmStatic
  @JvmOverloads
  fun StringBuilder.appendStyledInlineCode(@NlsSafe code: String, language: Language? = null, project: Project? = null): StringBuilder =
    append(INLINE_CODE_PREFIX)
      .appendHighlightedCode(
        DocumentationSettings.getInlineCodeHighlightingMode() == InlineCodeHighlightingMode.SEMANTIC_HIGHLIGHTING, code, language, project,
        true)
      .append(INLINE_CODE_SUFFIX)

  @JvmStatic
  @JvmOverloads
  fun getStyledInlineCodeFragment(code: String, language: Language? = null, project: Project? = null): String =
    StringBuilder().apply { appendStyledInlineCodeFragment(code, language, project) }.toString()

  @JvmStatic
  @JvmOverloads
  fun StringBuilder.appendStyledInlineCodeFragment(code: String, language: Language? = null, project: Project? = null): StringBuilder =
    appendHighlightedCode(true, code, language, project, false)

  @JvmStatic
  fun StringBuilder.appendStyledLinkFragment(contents: String, textAttributes: TextAttributes): StringBuilder =
    appendStyledSpan(DocumentationSettings.isSemanticHighlightingOfLinksEnabled(), textAttributes,
                     contents, false)

  @JvmStatic
  fun StringBuilder.appendStyledLinkFragment(contents: String, textAttributesKey: TextAttributesKey): StringBuilder =
    appendStyledSpan(DocumentationSettings.isSemanticHighlightingOfLinksEnabled(), textAttributesKey,
                     contents, false)

  @JvmStatic
  fun StringBuilder.appendStyledSignatureFragment(contents: String, textAttributes: TextAttributes): StringBuilder =
    appendStyledSpan(DocumentationSettings.isHighlightingOfQuickDocSignaturesEnabled(), textAttributes,
                     contents, false)

  @JvmStatic
  fun StringBuilder.appendStyledSignatureFragment(contents: String, textAttributesKey: TextAttributesKey): StringBuilder =
    appendStyledSpan(DocumentationSettings.isHighlightingOfQuickDocSignaturesEnabled(), textAttributesKey,
                     contents, false)

  @JvmStatic
  fun StringBuilder.appendStyledFragment(contents: String, textAttributes: TextAttributes): StringBuilder =
    appendStyledSpan(true, textAttributes, contents, false)

  @JvmStatic
  fun StringBuilder.appendStyledFragment(contents: String, textAttributesKey: TextAttributesKey): StringBuilder =
    appendStyledSpan(true, textAttributesKey, contents, false)

  @JvmStatic
  fun StringBuilder.appendWrappedWithInlineCodeTag(@NlsSafe contents: CharSequence): @NlsSafe StringBuilder =
    if (!contents.isBlank())
      append(INLINE_CODE_PREFIX, contents, INLINE_CODE_SUFFIX)
    else
      this

  @JvmStatic
  fun StringBuilder.wrapWithInlineCodeTag(): @NlsSafe StringBuilder =
    if (!isBlank())
      insert(0, INLINE_CODE_PREFIX)
        .append(INLINE_CODE_SUFFIX)
    else
      this

  @JvmStatic
  fun wrapWithInlineCodeTag(string: String): @NlsSafe String =
    if (!string.isBlank())
      INLINE_CODE_PREFIX + string + INLINE_CODE_SUFFIX
    else
      string

  @JvmStatic
  fun guessLanguage(language: String?): Language? =
    if (language == null)
      null
    else
      Language
        .findInstancesByMimeType(language)
        .asSequence()
        .plus(Language.findInstancesByMimeType("text/$language"))
        .plus(
          Language.getRegisteredLanguages()
            .asSequence()
            .filter { languageMatches(language, it) }
        )
        .firstOrNull()

  @JvmStatic
  fun getDefaultDocCodeStyles(colorScheme: EditorColorsScheme, backgroundColor: Color): List<String> {
    val result = mutableListOf<String>()

    // TODO: When removing `getMonospaceFontSizeCorrection` copy it's code here
    @Suppress("DEPRECATION", "removal")
    val definitionCodeFontSizePercent = DocumentationSettings.getMonospaceFontSizeCorrection(false)

    @Suppress("DEPRECATION", "removal")
    val contentCodeFontSizePercent = DocumentationSettings.getMonospaceFontSizeCorrection(true)

    result.addAllIfNotNull(
      "tt, code, pre, .pre { font-family:\"$EDITOR_FONT_NAME_NO_LIGATURES_PLACEHOLDER\"; font-size:$contentCodeFontSizePercent%; }",
      "pre {white-space: pre-wrap}",  // supported by JetBrains Runtime
      ".definition code, .definition pre, .definition-only code, .definition-only pre { font-size: $definitionCodeFontSizePercent% }",
    )

    if (!DocumentationSettings.isCodeBackgroundEnabled())
      return result

    val codeBg = ColorUtil.toHtmlColor(backgroundColor.let {
      val luminance = ColorUtil.getLuminance(it).toFloat()
      val change = if (luminance < 0.028f)
      // In case of a very dark theme, make background lighter
        1.55f - luminance * 10f
      else if (luminance < 0.15)
      // In any other case make background darker
        0.85f
      else
        0.96f
      ColorUtil.hackBrightness(it, 1, change)
    })
    val codeFg = ColorUtil.toHtmlColor(colorScheme.defaultForeground)

    val codeColorStyle = "{ background-color: $codeBg; color: $codeFg; }"

    if (DocumentationSettings.getInlineCodeHighlightingMode() !== InlineCodeHighlightingMode.NO_HIGHLIGHTING) {
      result.add(".content code, .content-separated code, .content-only div:not(.bottom) code, .sections code $codeColorStyle")
      result.add(
        ".content code, .content-separated code, .content-only div:not(.bottom) code, .sections code { padding: 1px 4px; margin: 1px 0px; caption-side: 10px; }")
    }
    if (DocumentationSettings.isHighlightingOfCodeBlocksEnabled()) {
      result.add("div.styled-code $codeColorStyle")
      result.add("div.styled-code {  margin: 5px 0px 5px 10px; padding: 6px; }")
      result.add("div.styled-code pre { padding: 0px; margin: 0px }")
    }
    return result
  }

  private fun StringBuilder.appendHighlightedCode(doHighlighting: Boolean, code: CharSequence, language: Language?, project: Project?,
                                                  isForRenderedDoc: Boolean): StringBuilder {
    val trimmedCode = code.toString().trim('\n', '\r').trimIndent().replace(' ', ' ')
    if (language != null && doHighlighting) {
      HtmlSyntaxInfoUtil.appendHighlightedByLexerAndEncodedAsHtmlCodeSnippet(
        this, project ?: DefaultProjectFactory.getInstance().defaultProject, language, trimmedCode,
        DocumentationSettings.getHighlightingSaturation(isForRenderedDoc))
    }
    else {
      append(XmlStringUtil.escapeString(trimmedCode))
    }
    return this
  }

  private fun StringBuilder.appendStyledSpan(doHighlighting: Boolean, attributesKey: TextAttributesKey,
                                             value: String?, isForRenderedDoc: Boolean): StringBuilder {
    if (doHighlighting) {
      HtmlSyntaxInfoUtil.appendStyledSpan(this, attributesKey, value, DocumentationSettings.getHighlightingSaturation(isForRenderedDoc))
    }
    else {
      append(XmlStringUtil.escapeString(value))
    }
    return this
  }

  private fun StringBuilder.appendStyledSpan(doHighlighting: Boolean, attributes: TextAttributes,
                                             value: String?, isForRenderedDoc: Boolean): StringBuilder {
    if (doHighlighting) {
      HtmlSyntaxInfoUtil.appendStyledSpan(this, attributes, value, DocumentationSettings.getHighlightingSaturation(isForRenderedDoc))
    }
    else {
      append(XmlStringUtil.escapeString(value))
    }
    return this
  }

  private fun languageMatches(langType: String, language: Language): Boolean =
    langType.equals(language.id, ignoreCase = true)
    || FileTypeManager.getInstance().getFileTypeByExtension(langType) === language.associatedFileType

}