// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.formatting.visualLayer

import com.intellij.application.options.CodeStyle
import com.intellij.codeInspection.incorrectFormatting.ChangeCollectingListener
import com.intellij.codeInspection.incorrectFormatting.ReplaceChange
import com.intellij.formatting.virtualFormattingListener
import com.intellij.formatting.visualLayer.VisualFormattingLayerElement.*
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.codeStyle.CodeStyleManager
import java.util.*
import java.util.Collections.synchronizedMap
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.min


@Service
class VisualFormattingLayerServiceImpl : VisualFormattingLayerService() {

  override fun getVisualFormattingLayerElements(file: PsiFile): List<VisualFormattingLayerElement> {
    val document = PsiDocumentManager.getInstance(file.project).getDocument(file) ?: return emptyList()

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

    return changeCollector
      .getChanges().asSequence()
      .filterIsInstance<ReplaceChange>()
      .flatMap { change -> formattingElements(document, change) }
      .filterNotNull()
      .toList()
  }

  private fun formattingElements(document: Document, change: ReplaceChange): Sequence<VisualFormattingLayerElement?> = sequence {
    val originalLines = document.getText(change.range).lines()
    val replacementLines = change.replacement.lines()

    val firstLine = document.getLineNumber(change.range.startOffset)
    val n = originalLines.size
    val m = replacementLines.size

    // This case needs soft wraps to visually split the existing line.
    // Not supported by API yet, so we will just skip it for now.
    if (1 == n && n < m) {
      return@sequence
    }

    // Fold first (N - M + 1) lines into one line of the replacement...
    if (n > m) {
      val startOffset = change.range.startOffset
      val endOffset = min(document.getLineEndOffset(firstLine + n - m), change.range.endOffset)
      yield(inlayOrFold(startOffset, endOffset, replacementLines[0]))
    }

    // ...or skip the first line by folding and add block inlay for (M - N) lines
    if (n <= m) {
      val startOffset = change.range.startOffset
      // This breaks down when (1 = N < M), but we've filtered out this case in the beginning
      val endOffset = min(document.getLineEndOffset(firstLine), change.range.endOffset)
      yield(inlayOrFold(startOffset, endOffset, replacementLines[0]))

      // add block inlay for M - N lines after firstLine, might be empty.
      yield(blockInlay(document.getLineStartOffset(firstLine + 1), m - n))
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
      val originalLineInDoc = firstLine + n - linesToProcess + i - 1      // goes up until last line inclusively
      val replacementLine = replacementLines[m - linesToProcess + i - 1]  // goes up until last replacement line inclusively
      val startOffset = document.getLineStartOffset(originalLineInDoc)
      val endOffset = min(document.getLineEndOffset(originalLineInDoc), change.range.endOffset)
      yield(inlayOrFold(startOffset, endOffset, replacementLine))
    }

  }

  // Visually replaces whitespace (or its absence) with a proper one
  private fun inlayOrFold(startOffset: Int, endOffset: Int, replacement: String): VisualFormattingLayerElement? {
    val inlayLength = replacement.length - (endOffset - startOffset)
    return when {
      inlayLength > 0 -> InlineInlay(startOffset, inlayLength)
      inlayLength < 0 -> Folding(startOffset, -inlayLength)
      else -> null
    }
  }

  private fun blockInlay(offset: Int, lines: Int): VisualFormattingLayerElement? {
    if (lines == 0) return null
    return BlockInlay(offset, lines)
  }

}
