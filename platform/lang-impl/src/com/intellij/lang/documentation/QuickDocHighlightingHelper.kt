// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.documentation

import com.intellij.icons.AllIcons
import com.intellij.ide.ui.html.StyleSheetRulesProviderForCodeHighlighting
import com.intellij.lang.Language
import com.intellij.lang.documentation.DocumentationSettings.InlineCodeHighlightingMode
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.editor.richcopy.HtmlSyntaxInfoUtil
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.psi.PsiElement
import com.intellij.ui.Graphics2DDelegate
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.ui.StartupUiUtil
import com.intellij.xml.util.XmlStringUtil
import org.jetbrains.annotations.ApiStatus.Internal
import java.awt.*
import java.awt.image.ImageObserver
import javax.swing.Icon
import javax.swing.text.Element
import javax.swing.text.FlowView
import javax.swing.text.View
import javax.swing.text.html.ImageView
import kotlin.math.max

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
  fun getDefaultFormattingStyles(): List<String> {
    val fontSize = StartupUiUtil.labelFont.getSize()
    return listOf(
      "h6 { font-size: ${fontSize + 2}}",
      "h5 { font-size: ${fontSize + 4}}",
      "h4 { font-size: ${fontSize + 6}}",
      "h3 { font-size: ${fontSize + 8}}",
      "h2 { font-size: ${fontSize + 10}}",
      "h1 { font-size: ${fontSize + 12}}",
      "h1, h2, h3, h4, h5, h6 {margin-top: 0; margin-bottom: 0; padding-top: 6}",
      "p { padding: 6px 0 2px 0; }",
      "ol { padding: 0px; margin-top: 6px; margin-bottom: 2px; }",
      "ul { padding: 0px; margin-top: 6px; margin-bottom: 2px; }",
      "li { padding: 1px 0 2px 0; }",
      "li p { padding-top: 0 }",
      "table p { padding-bottom: 0}",
      "th { text-align: left; }",
      "tr { margin: 0 0 0 0; padding: 0 0 0 0; }",
      "td { margin: 4px 0 0 0; padding: 2 0 2 0; }",
      "td p { padding-top: 0 }",
      "td pre { padding: 1px 0 0 0; margin: 0 0 0 0 }",
      ".centered { text-align: center}",
    )
  }

  @Internal
  @JvmStatic
  fun getDefaultDocCodeStyles(
    colorScheme: EditorColorsScheme,
    editorPaneBackgroundColor: Color,
  ): List<String> = StyleSheetRulesProviderForCodeHighlighting.getRules(
    colorScheme, editorPaneBackgroundColor,
    listOf(".content", ".content-separated", ".content-only div:not(.bottom)", ".sections"),
    listOf(".definition code", ".definition pre", ".definition-only code", ".definition-only pre"),
    DocumentationSettings.isCodeBackgroundEnabled()
    && DocumentationSettings.getInlineCodeHighlightingMode() !== InlineCodeHighlightingMode.NO_HIGHLIGHTING,
    DocumentationSettings.isCodeBackgroundEnabled()
    && DocumentationSettings.isHighlightingOfCodeBlocksEnabled(),
  )

  @Internal
  @JvmStatic
  fun getScalingImageViewExtension(): (Element, View) -> View? =
    { element, view -> if (view is ImageView) MyScalingImageView(element) else null }

  private fun StringBuilder.appendHighlightedCode(project: Project, language: Language?, doHighlighting: Boolean,
                                                  code: CharSequence, isForRenderedDoc: Boolean): StringBuilder {
    val trimmedCode = code.toString().trim('\n', '\r').trimIndent().replace(' ', ' ')
    if (language != null && doHighlighting) {
      HtmlSyntaxInfoUtil.appendHighlightedByLexerAndEncodedAsHtmlCodeSnippet(
        this, project, language, trimmedCode,
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

  private class MyScalingImageView(element: Element) : ImageView(element) {
    private var myAvailableWidth = 0

    override fun getLoadingImageIcon(): Icon =
      AllIcons.Process.Step_passive

    override fun getResizeWeight(axis: Int): Int =
      if (axis == X_AXIS) 1 else 0

    override fun getMaximumSpan(axis: Int): Float =
      getPreferredSpan(axis)

    override fun getPreferredSpan(axis: Int): Float {
      val baseSpan = super.getPreferredSpan(axis)
      if (axis == X_AXIS) {
        return baseSpan
      }
      else {
        var availableWidth = availableWidth
        if (availableWidth <= 0) return baseSpan
        val baseXSpan = super.getPreferredSpan(X_AXIS)
        if (baseXSpan <= 0) return baseSpan
        if (availableWidth > baseXSpan) {
          availableWidth = baseXSpan.toInt()
        }
        if (myAvailableWidth > 0 && availableWidth != myAvailableWidth) {
          preferenceChanged(null, false, true)
        }
        myAvailableWidth = availableWidth
        return baseSpan * availableWidth / baseXSpan
      }
    }

    private val availableWidth: Int
      get() {
        var v: View? = this
        while (v != null) {
          val parent = v.parent
          if (parent is FlowView) {
            val childCount = parent.getViewCount()
            for (i in 0 until childCount) {
              if (parent.getView(i) === v) {
                return parent.getFlowSpan(i)
              }
            }
          }
          v = parent
        }
        return 0
      }

    override fun paint(g: Graphics, a: Shape) {
      val targetRect = if ((a is Rectangle)) a else a.bounds
      val scalingGraphics: Graphics = object : Graphics2DDelegate(g as Graphics2D) {
        override fun drawImage(img: Image, x: Int, y: Int, width: Int, height: Int, observer: ImageObserver): Boolean {
          var paintWidth = width
          var paintHeight = height
          val maxWidth = max(0.0,
                             (targetRect.width - 2 * (x - targetRect.x)).toDouble()).toInt() // assuming left and right insets are the same
          val maxHeight = max(0.0,
                              (targetRect.height - 2 * (y - targetRect.y)).toDouble()).toInt() // assuming top and bottom insets are the same
          if (paintWidth > maxWidth) {
            paintHeight = paintHeight * maxWidth / paintWidth
            paintWidth = maxWidth
          }
          if (paintHeight > maxHeight) {
            paintWidth = paintWidth * maxHeight / paintHeight
            paintHeight = maxHeight
          }
          return super.drawImage(img, x, y, paintWidth, paintHeight, observer)
        }
      }
      super.paint(scalingGraphics, a)
    }
  }

}