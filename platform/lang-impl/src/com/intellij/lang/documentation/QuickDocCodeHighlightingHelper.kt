// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.documentation

import com.intellij.ide.ui.html.StyleSheetRulesProviderForCodeHighlighting
import com.intellij.lang.Language
import com.intellij.lang.documentation.DocumentationSettings.InlineCodeHighlightingMode
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.editor.richcopy.HtmlSyntaxInfoUtil
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.DefaultProjectFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.xml.util.XmlStringUtil
import java.awt.Color

object QuickDocCodeHighlightingHelper {

  const val CODE_BLOCK_PREFIX = StyleSheetRulesProviderForCodeHighlighting.CODE_BLOCK_PREFIX
  const val CODE_BLOCK_SUFFIX = StyleSheetRulesProviderForCodeHighlighting.CODE_BLOCK_SUFFIX

  const val INLINE_CODE_PREFIX = StyleSheetRulesProviderForCodeHighlighting.INLINE_CODE_PREFIX
  const val INLINE_CODE_SUFFIX = StyleSheetRulesProviderForCodeHighlighting.INLINE_CODE_SUFFIX

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
  fun getDefaultDocCodeStyles(
    colorScheme: EditorColorsScheme,
    editorPaneBackgroundColor: Color,
  ): List<String> = StyleSheetRulesProviderForCodeHighlighting.getRules(
    colorScheme, editorPaneBackgroundColor,
    listOf(".content", ".content-separated", ".content-only div:not(.bottom)", ".sections"),
    listOf(".definition code", ".definition pre", ".definition-only code", ".definition-only"),
    DocumentationSettings.isCodeBackgroundEnabled()
    && DocumentationSettings.getInlineCodeHighlightingMode() !== InlineCodeHighlightingMode.NO_HIGHLIGHTING,
    DocumentationSettings.isCodeBackgroundEnabled()
    && DocumentationSettings.isHighlightingOfCodeBlocksEnabled(),
  )

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