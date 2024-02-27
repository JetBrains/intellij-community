// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.documentation

import com.intellij.ide.ui.html.StyleSheetRulesProviderForCodeHighlighting
import com.intellij.lang.Language
import com.intellij.lang.documentation.DocumentationMarkup.*
import com.intellij.lang.documentation.DocumentationSettings.InlineCodeHighlightingMode
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.editor.richcopy.HtmlSyntaxInfoUtil
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.psi.PsiElement
import com.intellij.ui.scale.JBUIScale.scale
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.ui.StartupUiUtil
import com.intellij.xml.util.XmlStringUtil
import org.jetbrains.annotations.ApiStatus.Internal
import java.awt.Color

/**
 * This class facilitates generation of highlighted text and code for Quick Documentation.
 * It honors [DocumentationSettings], when highlighting code and links.
 */
object QuickDocHighlightingHelper {

  const val CODE_BLOCK_PREFIX = StyleSheetRulesProviderForCodeHighlighting.CODE_BLOCK_PREFIX
  const val CODE_BLOCK_SUFFIX = StyleSheetRulesProviderForCodeHighlighting.CODE_BLOCK_SUFFIX

  const val INLINE_CODE_PREFIX = StyleSheetRulesProviderForCodeHighlighting.INLINE_CODE_PREFIX
  const val INLINE_CODE_SUFFIX = StyleSheetRulesProviderForCodeHighlighting.INLINE_CODE_SUFFIX

  /**
   * The returned code block HTML (prefixed with [CODE_BLOCK_PREFIX] and suffixed with [CODE_BLOCK_SUFFIX])
   * has syntax highlighted, if there is language provided and
   * [DocumentationSettings.isHighlightingOfCodeBlocksEnabled] is `true`. The code block will
   * be rendered with a special background if [DocumentationSettings.isCodeBackgroundEnabled] is `true`.
   *
   * Any special HTML characters, like `<` or `>` are escaped.
   */
  @JvmStatic
  @RequiresReadLock
  fun getStyledCodeBlock(project: Project, language: Language?, code: @NlsSafe CharSequence): @NlsSafe String =
    StringBuilder().apply { appendStyledCodeBlock(project, language, code) }.toString()

  /**
   * Appends code block HTML (prefixed with [CODE_BLOCK_PREFIX] and suffixed with [CODE_BLOCK_SUFFIX]),
   * which has syntax highlighted, if there is language provided and
   * [DocumentationSettings.isHighlightingOfCodeBlocksEnabled] is `true`. The code block will
   * be rendered with a special background if [DocumentationSettings.isCodeBackgroundEnabled] is `true`.
   *
   * Any special HTML characters, like `<` or `>` are escaped.
   */
  @JvmStatic
  @RequiresReadLock
  fun StringBuilder.appendStyledCodeBlock(project: Project, language: Language?, code: @NlsSafe CharSequence): @NlsSafe StringBuilder =
    append(CODE_BLOCK_PREFIX)
      .appendHighlightedCode(project, language, DocumentationSettings.isHighlightingOfCodeBlocksEnabled(), code, true)
      .append(CODE_BLOCK_SUFFIX)

  @JvmStatic
  fun removeSurroundingStyledCodeBlock(string: String): String =
    string.trim().removeSurrounding(CODE_BLOCK_PREFIX, CODE_BLOCK_SUFFIX)

  /**
   * The returned inline code HTML (prefixed with [INLINE_CODE_PREFIX] and suffixed with [INLINE_CODE_SUFFIX])
   * has syntax highlighted, if there is language provided and
   * [DocumentationSettings.getInlineCodeHighlightingMode] is [DocumentationSettings.InlineCodeHighlightingMode.SEMANTIC_HIGHLIGHTING].
   * The code block will be rendered with a special background if [DocumentationSettings.isCodeBackgroundEnabled] is `true` and
   * [DocumentationSettings.getInlineCodeHighlightingMode] is not [DocumentationSettings.InlineCodeHighlightingMode.NO_HIGHLIGHTING].
   *
   * Any special HTML characters, like `<` or `>` are escaped.
   */
  @JvmStatic
  @RequiresReadLock
  fun getStyledInlineCode(project: Project, language: Language?, @NlsSafe code: String): @NlsSafe String =
    StringBuilder().apply { appendStyledInlineCode(project, language, code) }.toString()

  /**
   * Appends inline code HTML (prefixed with [INLINE_CODE_PREFIX] and suffixed with [INLINE_CODE_SUFFIX]),
   * which has syntax highlighted, if there is language provided and
   * [DocumentationSettings.getInlineCodeHighlightingMode] is [DocumentationSettings.InlineCodeHighlightingMode.SEMANTIC_HIGHLIGHTING].
   * The code block will be rendered with a special background if [DocumentationSettings.isCodeBackgroundEnabled] is `true` and
   * [DocumentationSettings.getInlineCodeHighlightingMode] is not [DocumentationSettings.InlineCodeHighlightingMode.NO_HIGHLIGHTING].
   *
   * Any special HTML characters, like `<` or `>` are escaped.
   */
  @JvmStatic
  @RequiresReadLock
  fun StringBuilder.appendStyledInlineCode(project: Project, language: Language?, @NlsSafe code: String): StringBuilder =
    append(INLINE_CODE_PREFIX)
      .appendHighlightedCode(
        project, language, DocumentationSettings.getInlineCodeHighlightingMode() == InlineCodeHighlightingMode.SEMANTIC_HIGHLIGHTING, code,
        true)
      .append(INLINE_CODE_SUFFIX)

  /**
   * Returns an HTML fragment containing [code] highlighted with [language].
   * Any special HTML characters, like `<` or `>` are escaped.
   *
   * Should not be used when generating Quick Doc signature or [PsiElement] links.
   */
  @JvmStatic
  @RequiresReadLock
  fun getStyledCodeFragment(project: Project, language: Language, @NlsSafe code: String): @NlsSafe String =
    StringBuilder().apply { appendStyledCodeFragment(project, language, code) }.toString()

  /**
   * Appends an HTML fragment containing [code] highlighted with [language].
   * Any special HTML characters, like `<` or `>` are escaped.
   *
   * Should not be used when generating Quick Doc signature or [PsiElement] links.
   */
  @JvmStatic
  @RequiresReadLock
  fun StringBuilder.appendStyledCodeFragment(project: Project, language: Language, @NlsSafe code: String): StringBuilder =
    appendHighlightedCode(project, language, true, code, false)

  /**
   * This method should be used when generating links to PsiElements.
   * Any special HTML characters, like `<` or `>` are escaped.
   *
   * Appends an HTML fragment containing [contents] colored according to [textAttributes]
   * if [DocumentationSettings.isSemanticHighlightingOfLinksEnabled] is `true`.
   */
  @JvmStatic
  fun StringBuilder.appendStyledLinkFragment(contents: String, textAttributes: TextAttributes): StringBuilder =
    appendStyledSpan(DocumentationSettings.isSemanticHighlightingOfLinksEnabled(), textAttributes, contents, false)

  /**
   * This method should be used when generating links to PsiElements.
   * Any special HTML characters, like `<` or `>` are escaped.
   *
   * Appends an HTML fragment containing [contents] colored according to [textAttributesKey]
   * if [DocumentationSettings.isSemanticHighlightingOfLinksEnabled] is `true`.
   */
  @JvmStatic
  fun StringBuilder.appendStyledLinkFragment(contents: String, textAttributesKey: TextAttributesKey): StringBuilder =
    appendStyledSpan(DocumentationSettings.isSemanticHighlightingOfLinksEnabled(), textAttributesKey, contents, false)

  /**
   * This method should be used when generating Quick Doc element signatures.
   * Special HTML characters, like `<` or `>` are **not** escaped.
   *
   * Appends an HTML fragment containing [contents] colored according to [textAttributes]
   * if [DocumentationSettings.isHighlightingOfQuickDocSignaturesEnabled] is `true`.
   */
  @JvmStatic
  fun StringBuilder.appendStyledSignatureFragment(contents: String, textAttributes: TextAttributes): StringBuilder =
    appendStyledSpan(DocumentationSettings.isHighlightingOfQuickDocSignaturesEnabled(), textAttributes,
                     contents, false)

  /**
   * This method should be used when generating Quick Doc element signatures.
   * Special HTML characters, like `<` or `>` are **not** escaped.
   *
   * Appends an HTML fragment containing [contents] colored according to [textAttributesKey]
   * if [DocumentationSettings.isHighlightingOfQuickDocSignaturesEnabled] is `true`.
   */
  @JvmStatic
  fun StringBuilder.appendStyledSignatureFragment(contents: String, textAttributesKey: TextAttributesKey): StringBuilder =
    appendStyledSpan(DocumentationSettings.isHighlightingOfQuickDocSignaturesEnabled(), textAttributesKey,
                     contents, false)

  /**
   * This method should be used when generating Quick Doc element signatures.
   * Any special HTML characters, like `<` or `>` are escaped.
   *
   * Returns an HTML fragment containing [code] highlighted with [language]
   * if [DocumentationSettings.isHighlightingOfQuickDocSignaturesEnabled] is `true`.
   */
  @JvmStatic
  @RequiresReadLock
  fun getStyledSignatureFragment(project: Project, language: Language?, code: String): @NlsSafe String =
    StringBuilder().apply { appendStyledSignatureFragment(project, language, code) }
      .toString()

  /**
   * This method should be used when generating Quick Doc element signatures.
   * Any special HTML characters, like `<` or `>` are escaped.
   *
   * Appends an HTML fragment containing [code] highlighted with [language]
   * if [DocumentationSettings.isHighlightingOfQuickDocSignaturesEnabled] is `true`.
   */
  @JvmStatic
  @RequiresReadLock
  fun StringBuilder.appendStyledSignatureFragment(project: Project, language: Language?, code: String): StringBuilder =
    appendHighlightedCode(project, language, DocumentationSettings.isHighlightingOfQuickDocSignaturesEnabled(), code, false)

  /**
   * Returns an HTML fragment containing [contents] colored according to [textAttributes].
   * Special HTML characters, like `<` or `>` are **not** escaped.
   *
   * Should not be used when generating Quick Doc signature or [PsiElement] links.
   */
  @JvmStatic
  fun getStyledFragment(contents: String, textAttributes: TextAttributes): String =
    StringBuilder().apply { appendStyledFragment(contents, textAttributes) }.toString()

  /**
   * Returns an HTML fragment containing [contents] colored according to [textAttributesKey].
   * Special HTML characters, like `<` or `>` are **not** escaped.
   *
   * Should not be used when generating Quick Doc signature or [PsiElement] links.
   */
  @JvmStatic
  fun getStyledFragment(contents: String, textAttributesKey: TextAttributesKey): String =
    StringBuilder().apply { appendStyledFragment(contents, textAttributesKey) }.toString()

  /**
   * Appends an  HTML fragment containing [contents] colored according to [textAttributes].
   * Special HTML characters, like `<` or `>` are **not** escaped.
   *
   * Should not be used when generating Quick Doc signature or [PsiElement] links.
   */
  @JvmStatic
  fun StringBuilder.appendStyledFragment(contents: String, textAttributes: TextAttributes): StringBuilder =
    appendStyledSpan(true, textAttributes, contents, false)

  /**
   * Appends an HTML fragment containing [contents] colored according to [textAttributesKey].
   * Special HTML characters, like `<` or `>` are **not** escaped.
   *
   * Should not be used when generating Quick Doc signature or [PsiElement] links.
   */
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

  /**
   * Tries to guess a registered IDE language based. Useful e.g. for Markdown support
   * to figure out a language to render a code block.
   */
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

  @Internal
  @JvmStatic
  fun getDefaultFormattingStyles(spacing: Int): List<String> {
    val fontSize = StartupUiUtil.labelFont.size
    return listOf(
      "h6 { font-size: ${fontSize + 1}}",
      "h5 { font-size: ${fontSize + 2}}",
      "h4 { font-size: ${fontSize + 3}}",
      "h3 { font-size: ${fontSize + 4}}",
      "h2 { font-size: ${fontSize + 6}}",
      "h1 { font-size: ${fontSize + 8}}",
      "h1, h2, h3, h4, h5, h6 {margin: 0 0 0 0; padding: 0 0 ${spacing}px 0; }",
      "p { margin: 0 0 0 0; padding: 0 0 ${spacing}px 0; line-height: 125%;}",
      "ul { margin: 0 0 0 ${scale(10)}px; padding: 0 0 ${spacing}px 0;}",
      "ol { margin: 0 0 0 ${scale(20)}px; padding: 0 0 ${spacing}px 0;}",
      "li { padding: ${scale(1)}px 0 ${scale(2)}px 0; }",
      "li p { padding-top: 0; padding-bottom: 0; }",
      "th { text-align: left; }",
      "tr, table { margin: 0 0 0 0; padding: 0 0 0 0; }",
      "td { margin: 0 0 0 0; padding: 0 ${spacing}px ${spacing}px 0; }",
      "td p { padding-top: 0; padding-bottom: 0; }",
      "td pre { padding: ${scale(1)}px 0 0 0; margin: 0 0 0 0 }",
      ".$CLASS_CENTERED { text-align: center}",
    )
  }

  @Internal
  @JvmStatic
  fun getDefaultDocCodeStyles(
    colorScheme: EditorColorsScheme,
    editorPaneBackgroundColor: Color,
    spacing: Int,
  ): List<String> = StyleSheetRulesProviderForCodeHighlighting.getRules(
    colorScheme, editorPaneBackgroundColor,
    listOf(".$CLASS_CONTENT", ".$CLASS_CONTENT_SEPARATED", ".$CLASS_CONTENT div:not(.$CLASS_BOTTOM)", ".$CLASS_CONTENT div:not(.$CLASS_TOP)",
           ".$CLASS_SECTIONS"),
    listOf(".$CLASS_DEFINITION code", ".$CLASS_DEFINITION pre", ".$CLASS_DEFINITION_SEPARATED code", ".$CLASS_DEFINITION_SEPARATED pre",
           ".$CLASS_BOTTOM code", ".$CLASS_TOP code"),
    DocumentationSettings.isCodeBackgroundEnabled()
    && DocumentationSettings.getInlineCodeHighlightingMode() !== InlineCodeHighlightingMode.NO_HIGHLIGHTING,
    DocumentationSettings.isCodeBackgroundEnabled()
    && DocumentationSettings.isHighlightingOfCodeBlocksEnabled(),
    "0 0 ${spacing}px 0"
  )

  private fun StringBuilder.appendHighlightedCode(project: Project, language: Language?, doHighlighting: Boolean,
                                                  code: CharSequence, isForRenderedDoc: Boolean): StringBuilder {
    val processedCode = code.toString().trim('\n', '\r').replace(' ', ' ').trimEnd()
    if (language != null && doHighlighting) {
      HtmlSyntaxInfoUtil.appendHighlightedByLexerAndEncodedAsHtmlCodeSnippet(
        this, project, language, processedCode,
        DocumentationSettings.getHighlightingSaturation(isForRenderedDoc))
    }
    else {
      append(XmlStringUtil.escapeString(processedCode.trimIndent()))
    }
    return this
  }

  private fun StringBuilder.appendStyledSpan(doHighlighting: Boolean, attributesKey: TextAttributesKey,
                                             value: String?, isForRenderedDoc: Boolean): StringBuilder {
    if (doHighlighting) {
      HtmlSyntaxInfoUtil.appendStyledSpan(this, attributesKey, value, DocumentationSettings.getHighlightingSaturation(isForRenderedDoc))
    }
    else {
      append(value)
    }
    return this
  }

  private fun StringBuilder.appendStyledSpan(doHighlighting: Boolean, attributes: TextAttributes,
                                             value: String?, isForRenderedDoc: Boolean): StringBuilder {
    if (doHighlighting) {
      HtmlSyntaxInfoUtil.appendStyledSpan(this, attributes, value, DocumentationSettings.getHighlightingSaturation(isForRenderedDoc))
    }
    else {
      append(value)
    }
    return this
  }

  private fun languageMatches(langType: String, language: Language): Boolean =
    langType.equals(language.id, ignoreCase = true)
    || FileTypeManager.getInstance().getFileTypeByExtension(langType) === language.associatedFileType

}