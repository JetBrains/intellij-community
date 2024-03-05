// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.documentation

import com.intellij.lang.documentation.DocumentationMarkup.*
import com.intellij.lang.documentation.QuickDocHighlightingHelper
import com.intellij.openapi.module.ModuleTypeManager
import com.intellij.openapi.module.UnknownModuleType
import com.intellij.ui.ColorUtil
import com.intellij.ui.components.JBHtmlPaneStyleSheetRulesProvider
import com.intellij.ui.scale.JBUIScale.scale
import com.intellij.util.containers.CollectionFactory
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.StyleSheetUtil
import com.intellij.util.ui.UIUtil
import org.intellij.lang.annotations.Language
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Contract
import java.util.function.Function
import javax.swing.Icon
import javax.swing.text.html.StyleSheet

@ApiStatus.Internal
object DocumentationHtmlUtil {

  @JvmStatic
  val contentOuterPadding: Int get() = 10

  // Should be minimum 2 to compensate mandatory table border width of 2
  @JvmStatic
  val contentInnerPadding: Int get() = 2

  @JvmStatic
  val spaceBeforeParagraph: Int get() = JBHtmlPaneStyleSheetRulesProvider.spaceBeforeParagraph

  @JvmStatic
  val spaceAfterParagraph: Int get() = JBHtmlPaneStyleSheetRulesProvider.spaceAfterParagraph

  @JvmStatic
  val docPopupPreferredMinWidth: Int get() = 300

  @JvmStatic
  val docPopupPreferredMaxWidth: Int get() = 500

  @JvmStatic
  val docPopupMinWidth: Int get() = 300

  @JvmStatic
  val docPopupMaxWidth: Int get() = 900

  @JvmStatic
  val docPopupMaxHeight: Int get() = 500

  @JvmStatic
  val lookupDocPopupWidth: Int get() = 450

  @JvmStatic
  val lookupDocPopupMinHeight: Int get() = 300

  @JvmStatic
  fun getModuleIconResolver(baseIconResolver: Function<in String?, out Icon?>): Function<in String?, out Icon?> = Function { key: String? ->
    baseIconResolver.apply(key)
    ?: ModuleTypeManager.getInstance().findByID(key)
      .takeIf { it !is UnknownModuleType }
      ?.icon
  }

  @JvmStatic
  fun getDocumentationPaneAdditionalCssRules(): List<StyleSheet> {
    val linkColor = ColorUtil.toHtmlColor(JBUI.CurrentTheme.Link.Foreground.ENABLED)
    val borderColor = ColorUtil.toHtmlColor(UIUtil.getTooltipSeparatorColor())
    val sectionColor = ColorUtil.toHtmlColor(DocumentationComponent.SECTION_COLOR)

    // When updating styles here, consider updating styles in DocRenderer#getStyleSheet
    val contentOuterPadding = scale(contentOuterPadding)
    val beforeSpacing = scale(spaceBeforeParagraph)
    val afterSpacing = scale(spaceAfterParagraph)
    val contentInnerPadding = scale(contentInnerPadding)

    @Suppress("CssUnusedSymbol")
    @Language("CSS")
    val result = """
        html { padding: 0 ${contentOuterPadding}px 0 ${contentOuterPadding}px; margin: 0 }
        body { padding: 0; margin: 0; overflow-wrap: anywhere;}
        pre  { white-space: pre-wrap; }
        a { color: $linkColor; text-decoration: none;}
        .$CLASS_DEFINITION, .$CLASS_DEFINITION_SEPARATED {    
          padding: ${beforeSpacing}px ${contentInnerPadding}px ${afterSpacing}px ${contentInnerPadding}px;
        }
        .$CLASS_DEFINITION pre, .$CLASS_DEFINITION_SEPARATED pre { 
          margin: 0; padding: 0;
        }
        .$CLASS_CONTENT, .$CLASS_CONTENT_SEPARATED {
          padding: 0 ${contentInnerPadding}px 0px ${contentInnerPadding}px;
          max-width: 100%;
        }
        .$CLASS_SEPARATED, .$CLASS_DEFINITION_SEPARATED, .$CLASS_CONTENT_SEPARATED {
          padding-bottom: ${beforeSpacing + afterSpacing}px;
          margin-bottom: ${afterSpacing}px;
          border-bottom: thin solid $borderColor;
        }
        .$CLASS_BOTTOM, .$CLASS_DOWNLOAD_DOCUMENTATION, .$CLASS_TOP { 
          padding: ${beforeSpacing}px ${contentInnerPadding}px ${afterSpacing}px ${contentInnerPadding}px;
        }
        .$CLASS_GRAYED { color: #909090; display: inline;}
        
        .$CLASS_SECTIONS { padding: 0 ${contentInnerPadding - 2}px 0 ${contentInnerPadding - 2}px 0; border-spacing: 0; }
        .$CLASS_SECTION { color: $sectionColor; padding-right: 4px; white-space: nowrap; }
      """.trimIndent()
    return listOf(StyleSheetUtil.loadStyleSheet(result))
  }

  private val dropPrecedingEmptyParagraphTags = CollectionFactory.createCharSequenceSet(false).also {
    it.addAll(listOf("ul", "ol", "h1", "h2", "h3", "h4", "h5", "h6", "p", "tr", "td"))
  }

  /**
   * This method allows preprocessing HTML code before feeding the DocumentationEditorPane with it.
   *
   * It performs some string transformations required to work around the limitations of the HTML support implementation.
   */
  @JvmStatic
  @Contract(pure = true)
  fun transpileForHtmlEditorPaneInput(text: String): String =
    HtmlEditorPaneInputTranspiler(text).process()

  /**
   * Transpiler performs some simple lexing to understand where are tags, attributes and text.
   *
   * For performance reasons, all the following actions are applied in a single run:
   * - Add `<wbr>` after `.` if surrounded by letters
   * - Add `<wbr>` after `]`, `)` or `/` followed by a char or digit
   * - Remove empty <p> before some tags - workaround for Swing html renderer not removing empty paragraphs before non-inline tags
   * - Replace `<blockquote>\\s*<pre>` with [QuickDocHighlightingHelper.CODE_BLOCK_PREFIX]
   * - Replace `</pre>\\s*</blockquote>` with [QuickDocHighlightingHelper.CODE_BLOCK_SUFFIX]
   * - Replace `<pre><code>` with [QuickDocHighlightingHelper.CODE_BLOCK_PREFIX]
   * - Replace `</code></pre>` with [QuickDocHighlightingHelper.CODE_BLOCK_SUFFIX]
   */
  private class HtmlEditorPaneInputTranspiler(text: String) {
    private val codePoints = text.codePoints().iterator()
    private val result = StringBuilder(text.length + 50)
    private var codePoint = codePoints.nextInt()
    private var openingTag = false
    private val tagStart = StringBuilder()
    private val tagName = StringBuilder()
    private val tagBuffer = StringBuilder()

    fun next(builder: StringBuilder = result) {
      builder.appendCodePoint(codePoint)
      codePoint = if (codePoints.hasNext())
        codePoints.nextInt()
      else
        -1
    }

    fun readTagStart() {
      assert(codePoint == '<'.code)
      tagStart.clear()
      tagName.clear()
      next(tagStart)
      if (codePoint == '/'.code) {
        openingTag = false
        next(tagStart)
      }
      else if (codePoint == '!'.code) {
        next(tagStart)
        if (consume("--", tagStart)) {
          skipUntil("->", tagStart)
        }
      }
      else {
        openingTag = true
      }
      if (!Character.isLetter(codePoint))
        return
      while (Character.isLetterOrDigit(codePoint) || codePoint == '-'.code) {
        next(tagName)
      }
      tagStart.append(tagName)
    }

    fun consume(text: String, builder: StringBuilder): Boolean {
      for (c in text) {
        if (codePoint != c.code) return false
        next(builder)
      }
      return true
    }

    fun skipUntil(text: String, builder: StringBuilder) {
      loop@ while (codePoint >= 0) {
        for (i in text.indices) {
          val c = text[i]
          if (codePoint != c.code) {
            if (i == 0) next(builder)
            continue@loop
          }
          next(builder)
        }
        return
      }
    }

    fun skipToTagEnd(builder: StringBuilder) {
      while (codePoint >= 0) {
        when (codePoint) {
          '>'.code -> {
            next(builder)
            break
          }
          '\''.code, '"'.code -> {
            val quoteStyle = codePoint
            next(builder)
            while (codePoint >= 0) {
              when (codePoint) {
                '\\'.code -> {
                  next(builder)
                  if (codePoint >= 0)
                    next(builder)
                }
                quoteStyle -> {
                  next(builder)
                  break
                }
                else -> next(builder)
              }
            }
          }
          else -> next(builder)
        }
      }
    }

    fun handleTag() {
      val isP = tagName.contentEquals("p", true)
      val isPre = tagName.contentEquals("pre", true)
      val isCode = tagName.contentEquals("code", true)
      val isBlockquote = tagName.contentEquals("blockquote", true)
      val isOpeningTag = openingTag
      if (!isP && !isPre && !(isCode && !isOpeningTag) && !(isBlockquote && isOpeningTag)) {
        result.append(tagStart)
        skipToTagEnd(result)
        return
      }

      tagBuffer.clear()
      tagBuffer.append(tagStart)
      skipToTagEnd(tagBuffer)
      if (isP || isBlockquote || (isPre && !isOpeningTag)) {
        // Skip whitespace
        while (codePoint >= 0 && Character.isWhitespace(codePoint)) {
          next(tagBuffer)
        }
      }
      if (codePoint == '<'.code) {
        readTagStart()
        if (isP) {
          // Remove empty <p> before some tags - workaround for Swing html renderer not removing empty paragraphs before non-inline tags
          if (tagName !in dropPrecedingEmptyParagraphTags) {
            result.append(tagBuffer)
          }
          handleTag()
        }
        else {
          // Replace <blockquote>\\s*<pre> with QuickDocHighlightingHelper.CODE_BLOCK_PREFIX
          // Replace </pre>\\s*</blockquote> with QuickDocHighlightingHelper.CODE_BLOCK_SUFFIX
          // Replace <pre><code> with QuickDocHighlightingHelper.CODE_BLOCK_PREFIX
          // Replace </code></pre> with QuickDocHighlightingHelper.CODE_BLOCK_SUFFIX
          val nextTag = if (isPre) {
            if (isOpeningTag) "code" else "blockquote"
          }
          else "pre"
          if (tagName.contentEquals(nextTag, true) && (isOpeningTag == openingTag)) {
            skipToTagEnd(tagBuffer)
            if (isCode || (isPre && !isOpeningTag)) {
              // trim trailing whitespace
              result.setLength(result.indexOfLast { !Character.isWhitespace(it) } + 1)
            }
            result.append(if (isOpeningTag) QuickDocHighlightingHelper.CODE_BLOCK_PREFIX else QuickDocHighlightingHelper.CODE_BLOCK_SUFFIX)
          }
          else {
            result.append(tagBuffer)
            handleTag()
          }
        }
      }
      else {
        result.append(tagBuffer)
      }
    }

    fun process(): String {
      if (!codePoints.hasNext()) return ""


      while (codePoint >= 0) {
        when {
          // break after dot if surrounded by letters
          Character.isLetter(codePoint) -> {
            next()
            if (codePoint == '.'.code) {
              next()
              if (Character.isLetter(codePoint)) {
                result.append("<wbr>")
              }
            }
          }
          // break after ], ) or / followed by a char or digit
          codePoint == ')'.code || codePoint == ']'.code || codePoint == '/'.code -> {
            next()
            if (Character.isLetterOrDigit(codePoint)) {
              result.append("<wbr>")
            }
          }
          // process tags
          codePoint == '<'.code -> {
            readTagStart()
            if (tagName.isEmpty()) {
              result.append(tagStart)
              continue
            }
            if (tagName.contentEquals("style", true)
                || tagName.contentEquals("title", true)
                || tagName.contentEquals("script", true)
                || tagName.contentEquals("textarea", true)) {
              result.append(tagStart)
              val curTag = tagName.toString()
              do {
                if (codePoint == '<'.code) {
                  readTagStart()
                  result.append(tagStart)
                  if (tagName.contentEquals(curTag, true) && !openingTag) {
                    skipUntil(">", result)
                    break
                  }
                }
                else next()
              }
              while (codePoint >= 0)
            }
            else
              handleTag()
          }
          else -> next()
        }
      }

      return result.toString()
    }

  }
}
