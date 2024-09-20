// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints

import com.intellij.codeInsight.hints.presentation.PresentationRenderer
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.util.TextRange
import com.intellij.util.text.CharArrayUtil
import org.jetbrains.annotations.ApiStatus.Internal
import java.util.regex.Pattern

@Internal
object InlayDumpUtil {
  val inlayPattern: Pattern = Pattern.compile("""^\h*/\*<# block (.*?)#>\*/\h*(\r\n|\r|\n)|/\*<#(.*?)#>\*/""", Pattern.MULTILINE)

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
    val range = TextRange(0, document.textLength)
    val inlineElements = model.getInlineElementsInRange(range.startOffset, range.endOffset)
    val afterLineElements = model.getAfterLineEndElementsInRange(range.startOffset, range.endOffset)
    val blockElements = model.getBlockElementsInRange(range.startOffset, range.endOffset)
    val inlays = mutableListOf<InlayData>()
    inlineElements.mapTo(inlays) { InlayData(it, InlayType.Inline) }
    afterLineElements.mapTo(inlays) { InlayData(it, InlayType.Inline) }
    blockElements.mapTo(inlays) { InlayData(it, InlayType.Block) }
    inlays.sortBy { it.effectiveOffset(document) }
    return buildString {
      var currentOffset = 0
      for (inlay in inlays) {
        if (filter != null) {
          if (!filter(inlay.inlay)) {
            continue
          }
        }
        val effectiveOffset = inlay.effectiveOffset(document)
        val nextOffset = effectiveOffset + offsetShift
        append(sourceText.subSequence(currentOffset, nextOffset))
        if (inlay.type == InlayType.Block && indentBlockInlays) {
          val belowLineStartOffset = effectiveOffset
          val indentEndOffset = CharArrayUtil.shiftForward(sourceText, effectiveOffset, " \t")
          append(sourceText.subSequence(belowLineStartOffset, indentEndOffset))
        }
        append(inlay.render(renderer))
        currentOffset = nextOffset
      }
      append(sourceText.substring(currentOffset, sourceText.length))
    }
  }

  @Internal
  fun extractEntries(text: String) : List<Pair<Int, String>> {
    val matcher = inlayPattern.matcher(text)
    val offsetToContent = ArrayList<Pair<Int, String>>()
    var previousOffsetWithoutInlays = 0
    var previousOffsetWithInlays = 0
    while (matcher.find()) {
      val startOffset = matcher.start()
      val endOffset = matcher.end()
      previousOffsetWithoutInlays += startOffset - previousOffsetWithInlays
      previousOffsetWithInlays = endOffset
      val content = text.subSequence(startOffset, endOffset)
      if (content.startsWith("/*<# block")) {
        throw NotImplementedError("Block inlays are not yet supported")
      }
      val strippedContent = content.substring(5, content.length - 5)
      offsetToContent.add(previousOffsetWithoutInlays to strippedContent)
    }
    return offsetToContent
  }

  internal data class InlayData(val inlay: Inlay<*>, val type: InlayType) {
    fun effectiveOffset(document: Document): Int {
      return when (type) {
        InlayType.Inline -> inlay.offset
        InlayType.Block -> {
          val offset = inlay.offset
          val lineNumber = document.getLineNumber(offset)
          document.getLineStartOffset(lineNumber)
        }
      }
    }

    fun render(r: (EditorCustomElementRenderer, Inlay<*>) -> String): String {
      return buildString {
        append("/*<# ")
        if (type == InlayType.Block) {
          append("block ")
        }
        append(r(inlay.renderer, inlay))
        append(" #>*/")
        if (type == InlayType.Block) {
          append('\n')
        }
      }
    }

    override fun toString(): String {
      val renderer = inlay.renderer
      if (renderer !is PresentationRenderer && renderer !is LinearOrderInlayRenderer<*>) error("renderer not supported")
      return buildString {
        append("/*<# ")
        if (type == InlayType.Block) {
          append("block ")
        }
        append(renderer.toString())
        append(" #>*/")
        if (type == InlayType.Block) {
          append('\n')
        }
      }
    }
  }

  enum class InlayType {
    Inline,
    Block
  }
}