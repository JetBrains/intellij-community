// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints

import com.intellij.codeInsight.hints.presentation.PresentationRenderer
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.Inlay
import com.intellij.util.text.CharArrayUtil
import org.jetbrains.annotations.ApiStatus.Internal
import java.util.regex.Pattern

@Internal
object InlayDumpUtil {
  val inlayPattern: Pattern =
    Pattern.compile("""^\h*/\*<# (block) (.*?)#>\*/\h*(?:\r\n|\r|\n)|/\*<#(.*?)#>\*/""", Pattern.MULTILINE or Pattern.DOTALL)

  fun removeHints(text: String) : String {
    return inlayPattern.matcher(text).replaceAll("")
  }

  @Internal
  fun dumpHintsInternal(
    sourceText: String,
    editor: Editor,
    filter: ((Inlay<*>) -> Boolean)? = null,
    renderer: (EditorCustomElementRenderer, Inlay<*>) -> String = { r, _ -> r.toString() },
    // if document has multiple injected files, a proper host offset should be passed
    offsetShift: Int = 0,
    indentBlockInlays: Boolean = false,
  ): String {
    val document = editor.document
    val model = editor.inlayModel
    val inlineElements = model.getInlineElementsInRange(0, document.textLength)
    val afterLineElements = model.getAfterLineEndElementsInRange(0, document.textLength)
    val blockElements = model.getBlockElementsInRange(0, document.textLength)
    val inlays = mutableListOf<InlayData>()
    inlineElements.mapTo(inlays) { InlayData(it, InlayType.Inline) }
    afterLineElements.mapTo(inlays) { InlayData(it, InlayType.Inline) }
    blockElements.mapTo(inlays) { InlayData(it, InlayType.BlockAbove) }
    inlays.sortBy { it.anchorOffset(document) }
    return buildString {
      var currentOffset = 0
      for (inlay in inlays) {
        if (filter != null) {
          if (!filter(inlay.inlay)) {
            continue
          }
        }
        val inlayAnchorOffset = inlay.anchorOffset(document)
        val nextOffset = inlayAnchorOffset + offsetShift
        append(sourceText.subSequence(currentOffset, nextOffset))
        if (inlay.type == InlayType.BlockAbove && indentBlockInlays) {
          val belowLineStartOffset = inlayAnchorOffset
          val indentEndOffset = CharArrayUtil.shiftForward(sourceText, inlayAnchorOffset, " \t")
          append(sourceText.subSequence(belowLineStartOffset, indentEndOffset))
        }
        append(inlay.render(renderer))
        currentOffset = nextOffset
      }
      append(sourceText.substring(currentOffset, sourceText.length))
    }
  }

  @Internal
  fun extractEntries(text: String) : List<ExtractedInlayInfo> {
    val matcher = inlayPattern.matcher(text)
    val extracted = ArrayList<ExtractedInlayInfo>()
    var previousOffsetWithoutInlays = 0
    var previousOffsetWithInlays = 0
    while (matcher.find()) {
      val startOffset = matcher.start()
      val endOffset = matcher.end()
      previousOffsetWithoutInlays += startOffset - previousOffsetWithInlays
      previousOffsetWithInlays = endOffset
      val inlayType = when (matcher.group(1)) {
        "block" -> InlayType.BlockAbove
        else -> InlayType.Inline
      }
      val inlayOffset = previousOffsetWithoutInlays
      val content = (matcher.group(2) ?: matcher.group(3) ?: "")
      extracted.add(ExtractedInlayInfo(inlayOffset, inlayType, content))
    }
    return extracted
  }

  private data class InlayData(val inlay: Inlay<*>, val type: InlayType) {
    fun anchorOffset(document: Document): Int {
      return when (type) {
        InlayType.Inline -> inlay.offset
        InlayType.BlockAbove -> {
          val offset = inlay.offset
          val lineNumber = document.getLineNumber(offset)
          document.getLineStartOffset(lineNumber)
        }
      }
    }

    fun render(r: (EditorCustomElementRenderer, Inlay<*>) -> String): String {
      return buildString {
        append("/*<# ")
        if (type == InlayType.BlockAbove) {
          append("block ")
        }
        append(r(inlay.renderer, inlay))
        append(" #>*/")
        if (type == InlayType.BlockAbove) {
          append('\n')
        }
      }
    }

    override fun toString(): String {
      val renderer = inlay.renderer
      if (renderer !is PresentationRenderer && renderer !is LinearOrderInlayRenderer<*>) error("renderer not supported")
      return render { r, _ -> r.toString() }
    }
  }

  @Internal
  enum class InlayType {
    Inline,
    BlockAbove
  }

  @Internal
  data class ExtractedInlayInfo(val offset: Int, val type: InlayType, val content: String)
}