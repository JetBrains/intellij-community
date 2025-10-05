// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.Inlay
import com.intellij.util.text.CharArrayUtil
import org.jetbrains.annotations.ApiStatus.Internal
import java.util.regex.Pattern

/**
 * Note that while functions here are marked as internal, the dump format they define is part of **public APIs** built on top of them
 * (e.g. previews for declarative `InlayHintProvider`s and base tests for code vision and inlay hints).
 * @see com.intellij.codeInsight.hints.declarative.impl.DeclarativeHintsProviderSettingsModel
 */
@Internal
object InlayDumpUtil {
  val inlayPattern: Pattern =
    Pattern.compile("""^\h*/\*<# (block) (.*?)#>\*/\h*(?:\r\n|\r|\n)|/\*<#(.*?)#>\*/""", Pattern.MULTILINE or Pattern.DOTALL)

  fun removeInlays(text: String): String {
    return inlayPattern.matcher(text).replaceAll("")
  }

  /**
   * @param renderBelowLineBlockInlaysBelowTheLine if set to `true` then below-lines block inlays will be really places below the line. Otherwise, they will be places above the line
   */
  fun dumpInlays(
    sourceText: String,
    editor: Editor,
    filter: ((Inlay<*>) -> Boolean)? = null,
    renderer: (EditorCustomElementRenderer, Inlay<*>) -> String = { r, _ -> r.toString() },
    // if document has multiple injected files, a proper host offset should be passed
    offsetShift: Int = 0,
    renderBelowLineBlockInlaysBelowTheLine: Boolean = false,
    indentBlockInlays: Boolean = false,
  ): String {
    val document = editor.document
    val model = editor.inlayModel

    fun List<Inlay<*>>.doFilter() = if (filter == null) this else filter { filter(it) }

    val inlineElements = model.getInlineElementsInRange(0, document.textLength).doFilter()
    val afterLineElements = model.getAfterLineEndElementsInRange(0, document.textLength).doFilter()
    val blockElements = model.getBlockElementsInRange(0, document.textLength).doFilter()
    val inlayData = mutableListOf<InlayData>()
    inlineElements.mapTo(inlayData) { InlayData(it.offset, InlayDumpPlacement.Inline, renderer(it.renderer, it)) }
    afterLineElements.mapTo(inlayData) {
      InlayData(
        // Visually, in the dump, we want it be rendered at the end of the line, as in the editor;
        // however, we'd lose the ability to test precise offsets using dumps.
        it.offset,
        InlayDumpPlacement.Inline,
        renderer(it.renderer, it)
      )
    }
    blockElements.mapTo(inlayData) { inlay ->
      when {
        renderBelowLineBlockInlaysBelowTheLine && inlay.placement == Inlay.Placement.BELOW_LINE -> {
          InlayData(
            with(document) { getLineStartOffset(getLineNumber(inlay.offset) + 1) },
            InlayDumpPlacement.BlockBelow,
            renderer(inlay.renderer, inlay)
          )
        }
        else -> {
          InlayData(
            // Here we choose the visually consistent way and so lose precise offsets in inlay dumps.
            // This is inconsistent with after-line-end inlays (see comment above),
            // but I keep it this way so that we don't break existing tests.
            with(document) { getLineStartOffset(getLineNumber(inlay.offset)) },
            InlayDumpPlacement.BlockAbove,
            renderer(inlay.renderer, inlay)
          )
        }
      }

    }
    inlayData.sortBy { it.anchorOffset }
    return dumpInlays(sourceText, inlayData, offsetShift, indentBlockInlays)
  }

  fun dumpInlays(
    sourceText: String,
    inlayData: List<InlayData>,
    offsetShift: Int = 0,
    indentBlockInlays: Boolean = false,
  ): String = buildString {
    var currentOffset = 0
    for ((anchorOffset, placement, text) in inlayData) {
      val renderOffset = anchorOffset + offsetShift
      append(sourceText.subSequence(currentOffset, renderOffset))
      if (placement.isBlockInlay && indentBlockInlays) {
        when (placement) {
          InlayDumpPlacement.BlockAbove -> {
            val indentStartOffset = CharArrayUtil.shiftBackwardUntil(sourceText, renderOffset, "\n") + 1
            val indentEndOffset = CharArrayUtil.shiftForward(sourceText, renderOffset, " \t")
            append(sourceText.subSequence(indentStartOffset, indentEndOffset))
          }
          InlayDumpPlacement.BlockBelow -> {
            val indentStartOffset = CharArrayUtil.shiftForward(sourceText, renderOffset, " \t")
            val indentEndOffset = CharArrayUtil.shiftForwardUntil(sourceText, renderOffset, "\n")
            append(sourceText.subSequence(indentStartOffset, indentEndOffset))
          }
          else -> {}
        }

      }
      appendInlay(text, placement)
      currentOffset = renderOffset
    }
    append(sourceText.substring(currentOffset, sourceText.length))
  }

  private fun StringBuilder.appendInlay(content: String, placement: InlayDumpPlacement) {
    append("/*<# ")
    if (placement.isBlockInlay) {
      append("block ")
    }
    append(content)
    append(" #>*/")
    if (placement.isBlockInlay) {
      append('\n')
    }
  }

  /**
   * [InlayData.anchorOffset] for block inlays will be any from the related line.
   */
  fun extractInlays(text: String): List<InlayData> {
    val matcher = inlayPattern.matcher(text)
    val extracted = mutableListOf<InlayData>()
    var previousOffsetWithoutInlays = 0
    var previousOffsetWithInlays = 0
    while (matcher.find()) {
      val startOffset = matcher.start()
      val endOffset = matcher.end()
      previousOffsetWithoutInlays += startOffset - previousOffsetWithInlays
      previousOffsetWithInlays = endOffset
      val inlayPlacement = when (matcher.group(1)) {
        "block" -> InlayDumpPlacement.BlockAbove
        else -> InlayDumpPlacement.Inline
      }
      val inlayOffset = previousOffsetWithoutInlays
      val content = (matcher.group(2) ?: matcher.group(3) ?: "")
      extracted.add(InlayData(inlayOffset, inlayPlacement, content))
    }
    return extracted
  }

  data class InlayData(val anchorOffset: Int, val placement: InlayDumpPlacement, val content: String)

  enum class InlayDumpPlacement(val isBlockInlay: Boolean) {
    // Note that in an inlay dump, inline inlay at lineEndOffset is the same as after-line-end inlay
    Inline(isBlockInlay = false),
    BlockAbove(isBlockInlay = true),
    BlockBelow(isBlockInlay = true),
  }
}