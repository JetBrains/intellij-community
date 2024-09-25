// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints.declarative.impl.util

import com.intellij.codeInsight.hints.InlayDumpUtil
import com.intellij.codeInsight.hints.InlayDumpUtil.InlayType
import com.intellij.codeInsight.hints.declarative.*
import com.intellij.codeInsight.hints.declarative.impl.DeclarativeInlayRenderer
import com.intellij.codeInsight.hints.declarative.impl.InlayPresentationList
import com.intellij.codeInsight.hints.declarative.impl.inlayRenderer.DeclarativeIndentedBlockInlayRenderer
import com.intellij.codeInsight.hints.declarative.impl.inlayRenderer.DeclarativeInlayRendererBase
import com.intellij.codeInsight.hints.declarative.impl.util.DeclarativeHintsDumpUtil.ParserException
import com.intellij.codeInsight.hints.declarative.impl.util.DeclarativeHintsDumpUtil.extractHints
import com.intellij.openapi.editor.Editor
import org.jetbrains.annotations.ApiStatus
import java.lang.Character.*

/**
 * Defines the hint dump format used for previews.
 * A declarative hint dump is also an inlay dump ([InlayDumpUtil]).
 * Inlays of an inlay dump "carry" the hints of a declarative hint dump.
 *
 * ##### Basics
 *
 * ```
 * 123/*<# foo #>*/456
 * ```
 * specifies a hint with the content `foo` in [InlineInlayPosition] at offset 3 with [HintFormat.default].
 *
 * ```
 * fun foo() {
 *   /*<# block [one] [two] #>*/
 *   print("Hello")
 * }
 * ```
 * specifies two hints in [AboveLineIndentedPosition] on the same line.
 * The returned offset can be any from the line below.
 *
 * Block inlays can carry zero or more hints. Inline inlays can carry zero or one hint.
 *
 * ##### Hint format
 *
 * To modify the format of the hint, special parser directives can be used.
 * ```
 * /*<# block fmt:colorKind=TextWithoutBackground,
 *                fontSize=ABitSmallerThanInEditor,
 *                marginPadding=MarginAndSmallerPadding #>*/
 * 123/*<# foo #>*/456
 * ```
 * specifies a single inline hint with the given format.
 * Possible format directive values correspond to constants in their respective enum classes.
 * The new format is applied to **all following hints**.
 *
 * Generally, the text of an inlay is a sequence of `fmt` directives and hint contents (delimited by `[...]`) separated by whitespace.
 * The text of an inline inlay is implicitly considered as hint content (i.e., implicitly wrapped with `[...]`).
 * ```
 * /*<#] fmt:colorKind=Parameter [foo] [#>*/
 * ```
 * escapes the hint content mode, sets the [HintColorKind], and specifies a single hint.
 *
 * ##### Hint content with brackets
 *
 * Inside hint content, nested bracket pairs are allowed; unpaired brackets must be escaped using `\`.
 * I.e. `/*<# 123[456] #>*/` is ok. `/*<# 123\[456 #>*/` is also ok, but `/*<# 123[456 #>*/` is a parse error.
 *
 *
 *
 * @see com.intellij.codeInsight.hints.declarative.impl.DeclarativeHintsProviderSettingsModel
 * @see com.intellij.codeInsight.hints.InlayDumpUtil
 */
@ApiStatus.Internal
object DeclarativeHintsDumpUtil {
  /**
   * @throws ParserException
   */
  @JvmStatic
  fun extractHints(source: String): List<ExtractedHintInfo> {
    val extractedInlays = InlayDumpUtil.extractEntries(source)
    val hintBuilder = object {
      val extractedHints = mutableListOf<ExtractedHintInfo>()

      var position: InlayPosition = InlineInlayPosition(0, true)
      var hintFormat: HintFormat = HintFormat.default

      fun build(text: String) {
        extractedHints.add(ExtractedHintInfo(position, text, hintFormat))
      }
    }

    // ensure above-line hints in a single extracted inlay (separated by '<|>') are assigned the same distinct vertical priority
    var verticalPriorityCounter = 0
    val inlayContentParser = InlayContentParser()
    for ((inlayOffset, renderType, inlayContent) in extractedInlays) {
      when (renderType) {
        InlayType.Inline -> hintBuilder.position = InlineInlayPosition(inlayOffset, true)
        InlayType.BlockAbove -> hintBuilder.position = AboveLineIndentedPosition(inlayOffset, verticalPriorityCounter++)
      }
      if (renderType == InlayType.Inline) {
        val parts = inlayContentParser.parse("[$inlayContent]")
        if (parts.isEmpty()) {
          parseFail("Expected hint content")
        }
        else if (parts.size == 1) {
          val content = parts[0] as? InlayPart.HintContent ?: error("Single part is not hint content")
          hintBuilder.build(content.text.trim())
        }
        else {
          if (inlayContent.isEmpty()) error("Inlay content is empty, but multiple parts found.")
          inlayContent.first().let { if (it != ']') parseFail("Expected ']', got '$it'. Perhaps there's unpaired unescaped ']' in your hint.") }
          inlayContent.last().let { if (it != '[') parseFail("Expected '[', got '$it'. Perhaps there's unpaired unescaped ']' in your hint.") }
          var content: String? = null
          var hintContentCounter = 0
          for (part in parts) {
            when (part) {
              is InlayPart.Directive -> hintBuilder.hintFormat = parseFmtDirective(part, hintBuilder.hintFormat)
              is InlayPart.HintContent -> {
                hintContentCounter++
                if (!part.text.isEmpty()) {
                  if (content != null) parseFail("Only single hint ('[...]') is allowed in inline inlay")
                  content = part.text
                }
              }
            }
          }
          if (hintContentCounter > 3) parseFail("Only one hint is allowed in inline inlay")
          if (content != null) {
            hintBuilder.build(content)
          }
        }
      }
      else {
        val parts = inlayContentParser.parse(inlayContent)
        for (part in parts) {
          when (part) {
            is InlayPart.Directive -> hintBuilder.hintFormat = parseFmtDirective(part, hintBuilder.hintFormat)
            is InlayPart.HintContent -> hintBuilder.build(part.text)
          }
        }
      }
    }

    return hintBuilder.extractedHints
  }

  data class ExtractedHintInfo(val position: InlayPosition, val text: String, val hintFormat: HintFormat)

  class ParserException(msg: String) : Exception(msg)

  /**
   * Dumps hints.
   *
   * Hint format is not included, only the contents of the hints.
   *
   * The generated dump will be readable by [extractHints] *iff* the output of [renderer] is correctly escaped hint content.
   */
  @JvmStatic
  fun dumpHints(
    sourceText: String,
    editor: Editor,
    renderer: (InlayPresentationList) -> String
  ): String {
    return InlayDumpUtil.dumpHintsInternal(
      sourceText,
      editor,
      indentBlockInlays = true,
      filter = { it.renderer is DeclarativeInlayRendererBase<*> },
      renderer = { inlayRenderer, _, inlayType ->
        inlayRenderer as DeclarativeInlayRendererBase<*>
        if (inlayType == InlayType.Inline) {
          val presentationList = inlayRenderer.presentationLists.singleOrNull()
          if (presentationList == null) {
            error("Inline declarative inlay must carry exactly one hint")
          }
          return@dumpHintsInternal renderer(presentationList)
        }
        require(inlayRenderer is DeclarativeIndentedBlockInlayRenderer)
        val presentationLists = inlayRenderer.presentationLists
        if (presentationLists.isEmpty()) {
          error("Declarative inlay renderer must carry at least one hint")
        }
        buildString {
          fun render(presentationList: InlayPresentationList) {
            append('[')
            append(renderer(presentationList))
            append(']')
          }
          render(presentationLists[0])
          for (i in 1..<presentationLists.size) {
            append(' ')
            render(presentationLists[i])
          }
        }
      }
    )
  }
}

private fun parseFail(msg: String): Nothing {
  throw ParserException(msg)
}

private fun parseFmtDirective(directive: InlayPart.Directive, currentFormat: HintFormat): HintFormat {
  if (directive.id != "fmt") parseFail("Unknown directive '${directive.id}'")

  var newFormat = currentFormat
  for (opt in directive.options) {
    val key = opt.first.lowercase()
    val value = opt.second
    when (key) {
      "colorkind" -> newFormat = newFormat.withColorKind(enumValueOf(value))
      "fontsize" -> newFormat = newFormat.withFontSize(enumValueOf(value))
      "marginpadding" -> newFormat = newFormat.withHorizontalMargin(enumValueOf(value))
      else -> parseFail("Unknown declarative hint 'fmt' directive option '$key'")
    }
  }
  return newFormat
}

private sealed interface InlayPart {
  data class HintContent(val text: String) : InlayPart
  data class Directive(val id: String, val options: List<Pair<String, String>>) : InlayPart
}

private class InlayContentParser() {
  private var offset = 0
  private val inlayParts = mutableListOf<InlayPart>()
  private var text: String = ""

  private var currentDirId: String = ""
  private var currentOptKey: String = ""
  private var currentOptValue: String = ""

  private val contentBuilder = StringBuilder()

  fun parse(text: String): List<InlayPart> {
    this.text = text
    inlayParts.clear()
    this.offset = 0
    parseMeta()
    return inlayParts
  }

  private fun parseMeta() {
    while (hasNextChar()) {
      val ch = nextChar()
      when {
        ch == '[' -> parseContent()
        isJavaIdentifierStart(ch) -> {
          offset--
          parseDir()
        }
        isWhitespace(ch) -> {}
        else -> fail("Unexpected meta character '$ch'")
      }
    }
  }

  private fun parseContent() {
    contentBuilder.clear()
    var nestingLevel = 1
    while (hasNextChar()) {
      val ch = nextChar()
      when (ch) {
        '[' -> {
          nestingLevel++
          contentBuilder.append(ch)
        }
        ']' -> {
          nestingLevel--
          if (nestingLevel > 0) {
            contentBuilder.append(ch)
          }
          else {
            inlayParts.add(InlayPart.HintContent(contentBuilder.toString()))
            return
          }
        }
        '\\' -> parseContentEscape()
        else -> contentBuilder.append(ch)
      }
    }
    fail("Expected ']'")
  }

  private fun parseContentEscape() {
    if (!hasNextChar()) fail("Expected character after '\\'")
    when (val ch = nextChar()) {
      '\\', ']', '[' -> contentBuilder.append(ch)
      else -> fail("Unknown escape sequence '\\$ch'")
    }
  }

  private fun parseDir() {
    parseDirId()
    val opts = mutableListOf<Pair<String, String>>()
    var moreOpts = true
    while (moreOpts) {
      moreOpts = parseDirOpt()
      opts.add(currentOptKey to currentOptValue)
    }
    inlayParts.add(InlayPart.Directive(currentDirId, opts))
  }

  private fun parseDirId() {
    val start = offset
    while (hasNextChar()) {
      val ch = nextChar()
      when {
        ch == ':' -> {
          currentDirId = text.substring(start, offset - 1)
          return
        }
        isJavaIdentifierPart(ch) -> {}
        else -> fail("Unexpected character '$ch' in directive id")
      }
    }
    fail("Expected ':' after directive id")
  }

  private fun parseDirOpt(): Boolean {
    parseDirOptKey()
    return parseDirOptValue()
  }

  private fun parseDirOptKey() {
    skipWhitespace()
    val start = offset
    if (hasNextChar()) {
      val ch = nextChar()
      if (!isJavaIdentifierStart(ch)) fail("Expected valid java identifier start char, got '$ch'")
    }
    while (hasNextChar()) {
      val ch = nextChar()
      when {
        ch == '=' -> {
          currentOptKey = text.substring(start, offset - 1)
          return
        }
        isJavaIdentifierPart(ch) -> {}
        else -> fail("Unexpected character '$ch' in directive key")
      }
    }
    fail("Expected '=' after directive key")
  }

  // true ⇒ more opts follow
  private fun parseDirOptValue(): Boolean {
    val start = offset
    while (hasNextChar()) {
      val ch = nextChar()
      when {
        ch == ',' -> {
          currentOptValue = text.substring(start, offset - 1)
          return true
        }
        isWhitespace(ch) -> {
          currentOptValue = text.substring(start, offset - 1)
          return false
        }
      }
    }
    currentOptValue = text.substring(start, offset)
    return false
  }

  private fun fail(message: String): Nothing {
    parseFail("'$message'\n  at $offset\n  in '$text'")
  }

  private fun nextChar() = text[offset++]

  private fun hasNextChar() = offset < text.length

  private fun skipWhitespace() {
    while (hasNextChar()) {
      if (!isWhitespace(nextChar())) {
        offset--
        return
      }
    }
  }
}