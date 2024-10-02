// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.formatting.visualLayer

import com.intellij.application.options.CodeStyle
import com.intellij.codeInspection.incorrectFormatting.FormattingChanges
import com.intellij.codeInspection.incorrectFormatting.detectFormattingChanges
import com.intellij.formatting.visualLayer.VisualFormattingLayerElement.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.util.EditorScrollingPositionKeeper
import com.intellij.openapi.editor.impl.LineSet
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiDocumentManager
import org.jetbrains.annotations.ApiStatus
import kotlin.math.min

@ApiStatus.Internal
@Service
class VisualFormattingLayerServiceImpl : VisualFormattingLayerService() {

  override fun applyVisualFormattingLayerElementsToEditor(editor: Editor, elements: List<VisualFormattingLayerElement>) {
    EditorScrollingPositionKeeper.perform(editor, false) {
      doApplyElements(editor, elements)
    }
  }

  private fun doApplyElements(editor: Editor, elements: List<VisualFormattingLayerElement>) {
    val oldInlineInlays = editor.inlayModel.getInlineElementsInRange(0, Int.MAX_VALUE, InlayPresentation::class.java)
    val oldBlockInlays = editor.inlayModel.getBlockElementsInRange(0, Int.MAX_VALUE, InlayPresentation::class.java)
    val newInlineInlays = elements.filterIsInstance<InlineInlay>()
    val newBlockInlays = elements.filterIsInstance<BlockInlay>()

    // Avoid calling execute in batch mode if it is not necessary, as it has side effects
    if (oldInlineInlays.isNotEmpty() || oldBlockInlays.isNotEmpty() || newInlineInlays.isNotEmpty() || newBlockInlays.isNotEmpty()) {
      editor.inlayModel.execute(true) {
        oldInlineInlays.forEach(Disposer::dispose)
        oldBlockInlays.forEach(Disposer::dispose)

        newInlineInlays.forEach { it.applyToEditor(editor) }
        newBlockInlays.forEach { it.applyToEditor(editor) }
      }
    }

    editor.foldingModel.runBatchFoldingOperation(
      {
        editor.foldingModel
          .allFoldRegions
          .filter { it.getUserData(visualFormattingElementKey) == true }
          .forEach { it.dispose() }

        elements.asSequence()
          .filterIsInstance<Folding>()
          .forEach { it.applyToEditor(editor) }
      }, true, false)
  }

  override fun collectVisualFormattingLayerElements(editor: Editor): List<VisualFormattingLayerElement> {
    if (!ApplicationManager.getApplication().isUnitTestMode && editor.document.isWritable) return emptyList()
    val project = editor.project ?: return emptyList()
    val file = PsiDocumentManager.getInstance(project).getPsiFile(editor.document) ?: return emptyList()
    val codeStyleSettings = editor.visualFormattingLayerCodeStyleSettings ?: return emptyList()

    var formattingChanges: FormattingChanges? = null
    CodeStyle.runWithLocalSettings(file.project, codeStyleSettings, Runnable {
      if (file.isValid) {
        formattingChanges = detectFormattingChanges(file)
      }
    })
    if (formattingChanges == null) {
      return emptyList()
    }

    val tabSize = codeStyleSettings.getTabSize(file.fileType)
    val formattedTextLineSet = LineSet.createLineSet(formattingChanges!!.postFormatText)
    return formattingChanges!!.mismatches
      .flatMap { mismatch ->
        formattingElements(editor.document, formattingChanges!!.postFormatText, formattedTextLineSet, mismatch, tabSize)
      }
      .filterNotNull()
      .toList()
  }

  private fun formattingElements(document: Document,
                                 formattedText: CharSequence,
                                 formattedTextLineSet: LineSet,
                                 mismatch: FormattingChanges.WhitespaceMismatch,
                                 tabSize: Int): Sequence<VisualFormattingLayerElement?> = sequence {
    val originalText = document.text

    val originalFirstLine = document.getLineNumber(mismatch.preFormatRange.startOffset)
    val replacementFirstLine = formattedTextLineSet.findLineIndex(mismatch.postFormatRange.startOffset)
    val n = document.getLineNumber(mismatch.preFormatRange.endOffset) - originalFirstLine + 1
    val m = mismatch.postFormatRange.let {
      formattedTextLineSet.findLineIndex(it.endOffset) - formattedTextLineSet.findLineIndex(it.startOffset)
    } + 1

    // This case needs soft wraps to visually split the existing line.
    // Not supported by API yet, so we will just skip it for now.
    if (1 == n && n < m) {
      return@sequence
    }

    // Fold first (N - M + 1) lines into one line of the replacement...
    if (n > m) {
      val originalStartOffset = mismatch.preFormatRange.startOffset
      val originalEndOffset = min(document.getLineEndOffset(originalFirstLine + n - m), mismatch.preFormatRange.endOffset)
      val originalLineStartOffset = document.getLineStartOffset(originalFirstLine)
      val replacementStartOffset = mismatch.postFormatRange.startOffset
      val replacementEndOffset = min(formattedTextLineSet.getLineEnd(replacementFirstLine), mismatch.postFormatRange.endOffset)
      val replacementLineStartOffset = formattedTextLineSet.getLineStart(replacementFirstLine)

      yieldAll(inlayOrFold(originalText, originalLineStartOffset, originalStartOffset, originalEndOffset,
                           formattedText, replacementLineStartOffset, replacementStartOffset, replacementEndOffset,
                           tabSize))
    }

    // ...or skip the first line by folding and add block inlay for (M - N) lines
    if (n <= m) {
      val originalStartOffset = mismatch.preFormatRange.startOffset
      // This breaks down when (1 = N < M), but we've filtered out this case in the beginning
      val originalEndOffset = min(document.getLineEndOffset(originalFirstLine), mismatch.preFormatRange.endOffset)
      val originalLineStartOffset = document.getLineStartOffset(originalFirstLine)
      val replacementStartOffset = mismatch.postFormatRange.startOffset
      val replacementEndOffset = min(formattedTextLineSet.getLineEnd(replacementFirstLine), mismatch.postFormatRange.endOffset)
      val replacementLineStartOffset = formattedTextLineSet.getLineStart(replacementFirstLine)

      yieldAll(inlayOrFold(originalText, originalLineStartOffset, originalStartOffset, originalEndOffset,
                           formattedText, replacementLineStartOffset, replacementStartOffset, replacementEndOffset,
                           tabSize))

      // add block inlay for M - N lines after firstLine, might be empty.
      yield(blockInlay(document.getLineStartOffset(originalFirstLine + 1), m - n))
    }


    /*
      Now we have processed some lines. Both original whitespace and replacement
      have similar number lines left to process. This number depends on (N < M)
      but is always equal to MIN(N, M) - 1. See diagram for clarifying:


         N        >       M                  N         <=        M

      ┌─────┐ ─────┐                               Fold/Inlay ┌─────┐
      │  1  │      │                               ┌────────► │  1  │
      ├─────┤      │                               │ ┌─────── ├─────┤
      │     │ Fold │                               │ │        │     │
      │N - M│      │   ┌─────┐            ┌─────┐  │ │ Block  │M - N│
      │     │      ├─► │  1  │            │  1  │ ─┘ │ Inlay  │     │
      ├─────┤ ─────┘   ├─────┤            ├─────┤ ◄──┴─────── ├─────┤
      │     │          │     │            │     │             │     │
      │     │          │     │            │     │             │     │
      │     │Fold/Inlay│     │            │     │ Fold/Inlay  │     │
      │M - 1│ line by  │M - 1│            │N - 1│  line by    │N - 1│
      │     │    line  │     │            │     │     line    │     │
      │     │ ───────► │     │            │     │ ──────────► │     │
      │     │          │     │            │     │             │     │
      └─────┘          └─────┘            └─────┘             └─────┘
    */

    // Fold the rest lines one by one
    val linesToProcess = min(n, m) - 1
    for (i in 1..linesToProcess) {
      val originalLine = originalFirstLine + n - linesToProcess + i - 1      // goes up until last line inclusively
      val replacementLine = replacementFirstLine + m - linesToProcess + i - 1  // goes up until last replacement line inclusively
      val originalLineStartOffset = document.getLineStartOffset(originalLine)
      val originalEndOffset = min(document.getLineEndOffset(originalLine), mismatch.preFormatRange.endOffset)
      val replacementLineStartOffset = formattedTextLineSet.getLineStart(replacementLine)
      val replacementEndOffset = min(formattedTextLineSet.getLineEnd(replacementLine), mismatch.postFormatRange.endOffset)
      yieldAll(inlayOrFold(originalText, originalLineStartOffset, originalLineStartOffset, originalEndOffset,
                           formattedText, replacementLineStartOffset, replacementLineStartOffset, replacementEndOffset,
                           tabSize))
    }

  }

  // Visually replaces whitespace (or its absence) with a proper one
  private fun inlayOrFold(original: CharSequence,
                          originalLineStartOffset: Int,
                          originalStartOffset: Int,
                          originalEndOffset: Int,
                          formatted: CharSequence,
                          replacementLineStartOffset: Int,
                          replacementStartOffset: Int,
                          replacementEndOffset: Int,
                          tabSize: Int) = sequence {
    val (originalColumns, originalContainsTabs) = countColumnsWithinLine(original, originalLineStartOffset, originalStartOffset,
                                                                         originalEndOffset, tabSize)
    val (replacementColumns, _) = countColumnsWithinLine(formatted, replacementLineStartOffset, replacementStartOffset,
                                                         replacementEndOffset, tabSize)

    val columnsDelta = replacementColumns - originalColumns
    when {
      columnsDelta > 0 -> yield(InlineInlay(originalEndOffset, columnsDelta))
      columnsDelta < 0 -> {
        val originalLength = originalEndOffset - originalStartOffset
        if (originalContainsTabs) {
          yield(Folding(originalStartOffset, originalLength))
          if (replacementColumns > 0) {
            yield(InlineInlay(originalEndOffset, replacementColumns))
          }
        }
        else {
          yield(Folding(originalStartOffset, -columnsDelta))
        }
      }
    }
  }

  private fun blockInlay(offset: Int, lines: Int): VisualFormattingLayerElement? {
    if (lines == 0) return null
    return BlockInlay(offset, lines)
  }
}

private fun countColumnsWithinLine(sequence: CharSequence,
                                   lineStartOffset: Int,
                                   fromOffset: Int,
                                   untilOffset: Int,
                                   tabSize: Int): Pair<Int, Boolean> {
  val (startColumn, _) = countColumns(sequence, lineStartOffset, fromOffset, 0, tabSize)
  return countColumns(sequence, fromOffset, untilOffset, startColumn, tabSize)
}

private fun countColumns(sequence: CharSequence,
                         fromOffset: Int,
                         untilOffset: Int,
                         startColumn: Int,
                         tabSize: Int): Pair<Int, Boolean> {
  var cols = 0
  var tabStopOffset = startColumn % tabSize
  var containsTabs = false
  for (offset in fromOffset until untilOffset) {
    when (sequence[offset]) {
      '\t' -> {
        cols += tabSize - tabStopOffset
        tabStopOffset = 0
        containsTabs = true
      }
      '\n' -> {
        break
      }
      else -> {
        cols += 1
        tabStopOffset = (tabStopOffset + 1) % tabSize
      }
    }
  }
  return Pair(cols, containsTabs)
}
