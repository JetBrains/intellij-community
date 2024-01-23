// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.documentation

import com.intellij.lang.Language
import com.intellij.lang.documentation.DocumentationSettings.InlineCodeHighlightingMode
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.richcopy.HtmlSyntaxInfoUtil
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.DefaultProjectFactory
import com.intellij.openapi.project.Project
import com.intellij.xml.util.XmlStringUtil

object QuickDocCodeHighlightingHelper {

  const val CODE_BLOCK_PREFIX = "<div class='styled-code'><pre style=\"padding: 0px; margin: 0px\">"
  const val CODE_BLOCK_SUFFIX = "</pre></div>"

  const val INLINE_CODE_PREFIX = "<code>"
  const val INLINE_CODE_SUFFIX = "</code>"

  @JvmStatic
  fun getStyledCodeBlock(code: CharSequence, language: Language?, project: Project? = null): String =
    StringBuilder().apply { appendStyledCodeBlock(code, language, project) }.toString()

  @JvmStatic
  fun StringBuilder.appendStyledCodeBlock(code: CharSequence, language: Language?, project: Project? = null): StringBuilder =
    append(CODE_BLOCK_PREFIX)
      .appendHighlightedCode(DocumentationSettings.isHighlightingOfCodeBlocksEnabled(), code, language, project, true)
      .append(CODE_BLOCK_SUFFIX)

  @JvmStatic
  fun removeSurroundingStyledCodeBlock(string: String): String =
    string.trim().removeSurrounding(CODE_BLOCK_PREFIX, CODE_BLOCK_SUFFIX)

  @JvmStatic
  @JvmOverloads
  fun getStyledInlineCode(code: String, language: Language? = null, project: Project? = null): String =
    StringBuilder().apply { appendStyledInlineCode(code, language, project) }.toString()

  @JvmStatic
  @JvmOverloads
  fun StringBuilder.appendStyledInlineCode(code: String, language: Language? = null, project: Project? = null): StringBuilder =
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
  fun StringBuilder.appendStyledInlineCodeFragment(contents: String, textAttributesKey: TextAttributesKey): StringBuilder =
    appendStyledSpan(DocumentationSettings.isSemanticHighlightingOfLinksEnabled(), textAttributesKey,
                     contents, false)

  @JvmStatic
  fun StringBuilder.appendStyledLinkFragment(contents: String, textAttributesKey: TextAttributesKey): StringBuilder =
    appendStyledSpan(DocumentationSettings.isSemanticHighlightingOfLinksEnabled(), textAttributesKey,
                     contents, false)

  @JvmStatic
  fun StringBuilder.appendStyledSignatureFragment(contents: String, textAttributesKey: TextAttributesKey): StringBuilder =
    appendStyledSpan(DocumentationSettings.isHighlightingOfQuickDocSignaturesEnabled(), textAttributesKey,
                     contents, false)

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

  private fun StringBuilder.appendHighlightedCode(doHighlighting: Boolean, code: CharSequence, language: Language?, project: Project?,
                                                  isForRenderedDoc: Boolean): StringBuilder {
    val trimmedCode = code.toString().trim('\n', '\r').trimIndent()
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

  private fun languageMatches(langType: String, language: Language): Boolean =
    langType.equals(language.id, ignoreCase = true)
    || FileTypeManager.getInstance().getFileTypeByExtension(langType) === language.associatedFileType

}