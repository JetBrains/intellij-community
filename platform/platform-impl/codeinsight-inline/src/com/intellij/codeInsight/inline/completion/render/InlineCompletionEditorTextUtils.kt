// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.render

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.HighlighterColors
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.highlighter.HighlighterIterator
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.util.TextRange

internal object InlineCompletionEditorTextUtils {

  private val LOG = thisLogger()

  /**
   * Returns a list of text attributes for [range] in [editor].
   * It takes into account background color of the current line in the editor. It adds it if the following:
   * * [isInline] is true (meaning that these symbols are rendered in the same line)
   * * [range] is location on the same line as the caret
   */
  fun getBlocksForRealText(
    editor: Editor,
    range: TextRange,
    isInline: Boolean,
  ): List<InlineCompletionRenderTextBlock> {
    if (range.isEmpty) {
      return emptyList()
    }
    val highlighterIterator = editor.highlighter.createIterator(range.startOffset)
    val blocks = mutableListOf<InlineCompletionRenderTextBlock>()
    val processedText = StringBuilder()
    while (!highlighterIterator.atEnd() && highlighterIterator.start < range.endOffset) {
      val iteratorRange = TextRange(highlighterIterator.start, highlighterIterator.end)
      val intersection = range.intersection(iteratorRange)
      if (!intersection.isEmpty) {
        val text = editor.document.getText(intersection)
        val attributes = highlighterIterator.getTextAttributesOrDefault(editor)
        blocks += InlineCompletionRenderTextBlock(text, attributes)
        processedText.append(text)
      }
      highlighterIterator.advance()
    }
    val rangeText = editor.document.getText(range)
    if (blocks.isEmpty()) {
      blocks += InlineCompletionRenderTextBlock(rangeText, editor.getDefaultTextAttributes())
    }
    else {
      if (processedText.toString() != rangeText) {
        LOG.error("Highlighting for inline completion is incorrectly computed. Expected: $rangeText, actual: $processedText")
      }
    }

    if (useCaretLineBackground(editor, range, isInline)) {
      val caretRowColor = editor.colorsScheme.getColor(EditorColors.CARET_ROW_COLOR)
      blocks.replaceAll { block ->
        block.copy(attributes = block.attributes.clone().apply {
          if (backgroundColor == null) {
            backgroundColor = caretRowColor
          }
        })
      }
    }
    return blocks
  }

  private fun HighlighterIterator.getTextAttributesOrDefault(editor: Editor): TextAttributes {
    return textAttributes?.takeIf { !it.isEmpty } ?: editor.getDefaultTextAttributes()
  }

  private fun Editor.getDefaultTextAttributes(): TextAttributes {
    return colorsScheme.getAttributes(HighlighterColors.TEXT) ?: TextAttributes()
  }

  private fun useCaretLineBackground(editor: Editor, range: TextRange, isInline: Boolean): Boolean {
    if (!isInline) {
      return false
    }
    val caretOffset = editor.caretModel.offset
    val caretLine = editor.offsetToVisualLine(caretOffset, true)
    val rangeLine = editor.offsetToVisualLine(range.startOffset, true)
    return caretLine == rangeLine
  }
}
