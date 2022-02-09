// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.formatting.visualLayer

import com.intellij.application.options.CodeStyle
import com.intellij.codeInspection.incorrectFormatting.ChangeCollectingListener
import com.intellij.codeInspection.incorrectFormatting.ReplaceChange
import com.intellij.formatting.virtualFormattingListener
import com.intellij.openapi.components.Service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.FoldingModelEx
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.codeStyle.CodeStyleManager
import kotlin.math.min


val visualLayerKey = Key.create<Boolean>("BELONGS_TO_VISUAL_FORMATTING_LAYER")


@Service
class VisualFormattingLayerServiceImpl : VisualFormattingLayerService() {

  override fun Editor.removeVisualElements() {
    inlayModel
      .getInlineElementsInRange(0, Int.MAX_VALUE, VisualFormattingLayerInlayRenderer::class.java)
      .forEach { Disposer.dispose(it) }

    inlayModel
      .getBlockElementsInRange(0, Int.MAX_VALUE, VisualFormattingLayerInlayRenderer::class.java)
      .forEach { Disposer.dispose(it) }

    foldingModel.runBatchFoldingOperation {
      foldingModel.allFoldRegions
        .filter { it.getUserData(visualLayerKey) == true }
        .forEach { foldingModel.removeFoldRegion(it) }
    }
  }

  override fun Editor.addVisualElements() {
    val docManager = PsiDocumentManager.getInstance(project ?: return)
    docManager.commitDocument(document)
    val file = docManager.getPsiFile(document) ?: return

    val changeCollector = ChangeCollectingListener(file, document.text)

    try {
      file.virtualFormattingListener = changeCollector
      val style = scheme.codeStyleSettings
      CodeStyle.doWithTemporarySettings(file.project, style, Runnable {
        if (file.isValid) {
          CodeStyleManager.getInstance(file.project).reformat(file, true)
        }
      })
    }
    finally {
      file.virtualFormattingListener = null
    }

    val replaces = changeCollector
      .getChanges()
      .filterIsInstance<ReplaceChange>()

    replaces.forEach { change: ReplaceChange ->
      addVisualFormattingElements(change.range, change.replacement)
    }

  }


  private fun Editor.addVisualFormattingElements(range: TextRange, replacement: String) {
    val originalLines = document.getText(range).lines()
    val replacementLines = replacement.lines()

    val firstLine = document.getLineNumber(range.startOffset)
    val n = originalLines.size
    val m = replacementLines.size

    // This case needs soft wraps to visually split the existing line.
    // Not supported by API yet, so we will just skip it for now.
    if (1 == n && n < m) {
      return
    }

    // Fold first (N - M + 1) lines into one line of the replacement...
    if (n > m) {
      val startOffset = range.startOffset
      val endOffset = min(document.getLineEndOffset(firstLine + n - m), range.endOffset)
      visualReplace(startOffset, endOffset, replacementLines[0])
    }

    // ...or skip the first line by folding and add block inlay for (M - N) lines
    if (n <= m) {
      val startOffset = range.startOffset
      // This breaks down when (1 = N < M), but we've filtered out this case in the beginning
      val endOffset = min(document.getLineEndOffset(firstLine), range.endOffset)
      visualReplace(startOffset, endOffset, replacementLines[0])
      // add block inlay for M - N lines after firstLine, might be empty.
      addBlockInlay(firstLine, m - n)
    }


    /*
      Now we have processed some lines. Both original whitespace and replacement
      have similar number lines left to process. This number depends on (N < M)
      but is always equal to MIN(N, M) - 1. See diagram for clarifying:


         N       >       M                  N         <=        M

      ┌─────┐ ────┐                                   Fold   ┌─────┐
      │  1  │     │                               ┌────────► │  1  │
      ├─────┤     │                               │ ┌─────── ├─────┤
      │     │ Fold│                               │ │        │     │
      │N - M│     │   ┌─────┐            ┌─────┐  │ │ Block  │M - N│
      │     │     ├─► │  1  │            │  1  │ ─┘ │ Inlay  │     │
      ├─────┤ ────┘   ├─────┤            ├─────┤ ◄──┴─────── ├─────┤
      │     │         │     │            │     │             │     │
      │     │         │     │            │     │             │     │
      │     │ Fold    │     │            │     │   Fold      │     │
      │M - 1│ line by │M - 1│            │N - 1│   line by   │N - 1│
      │     │   line  │     │            │     │     line    │     │
      │     │ ──────► │     │            │     │ ──────────► │     │
      │     │         │     │            │     │             │     │
      └─────┘         └─────┘            └─────┘             └─────┘
    */

    // Fold the rest lines one by one
    val linesToProcess = min(n, m) - 1
    for (i in 1..linesToProcess) {
      val originalLineInDoc = firstLine + n - linesToProcess + i - 1      // goes up until last line inclusively
      val replacementLine = replacementLines[m - linesToProcess + i - 1]  // goes up until last replacement line inclusively
      val startOffset = document.getLineStartOffset(originalLineInDoc)
      val endOffset = min(document.getLineEndOffset(originalLineInDoc), range.endOffset)
      visualReplace(startOffset, endOffset, replacementLine)
    }

  }

  // Visually replaces whitespace (or its absence) with a proper one
  private fun Editor.visualReplace(startOffset: Int, endOffset: Int, replacement: String) {
    if (startOffset == endOffset && replacement.isNotEmpty()) {
      inlayModel.addInlineElement(startOffset, VisualFormattingLayerInlayRenderer(replacement))
    }
    else {
      foldingModel.runBatchFoldingOperation {
        (foldingModel as? FoldingModelEx)
          ?.createFoldRegion(startOffset, endOffset, replacement, null, true)
          ?.also {
            it.isExpanded = false
            it.putUserData(visualLayerKey, true)
          }
      }
    }
  }

  private fun Editor.addBlockInlay(afterLine: Int, lines: Int) {
    if (lines == 0) return
    inlayModel.addBlockElement(
      document.getLineStartOffset(afterLine + 1),
      true,  // relatesToPrecedingText
      true,  // showAbove, i.e. below of the given line
      0,
      VisualFormattingLayerInlayRenderer("\n".repeat(lines))
    )
  }

}
