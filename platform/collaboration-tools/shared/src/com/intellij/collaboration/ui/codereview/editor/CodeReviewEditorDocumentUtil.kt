// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.editor

import com.intellij.diff.util.DiffUtil
import com.intellij.diff.util.Range
import com.intellij.openapi.application.EdtImmediate
import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.ex.DocumentTracker
import com.intellij.openapi.vcs.ex.LineStatusTrackerBase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus

/**
 * Document helpers needed by the review-in-editor renderers.
 *
 * Kept separate from `ReviewInEditorUtil` because the renderers must be loadable on a split-mode frontend, while
 * `ReviewInEditorUtil` also owns the inspection-widget toolbar and therefore pulls in actions and the message bundle.
 */
@ApiStatus.Internal
object CodeReviewEditorDocumentUtil {

  fun isLastBlankLine(document: Document, lineIdx: Int): Boolean {
    val lineCount = DiffUtil.getLineCount(document)
    if (lineIdx != lineCount - 1) return false
    val start = document.getLineStartOffset(lineIdx)
    val end = document.getLineEndOffset(lineIdx)
    return start == end
  }

  /**
   * Maps a line index in the "before" side of [ranges] to the corresponding line in the "after" side.
   */
  fun transferLineToAfter(ranges: List<Range>, line: Int): Int {
    if (ranges.isEmpty()) return line
    var result = line
    for (range in ranges) {
      if (line in range.start1 until range.end1) {
        return (range.end2 - 1).coerceAtLeast(0)
      }

      if (range.end1 > line) return result

      val length1 = range.end1 - range.start1
      val length2 = range.end2 - range.start2
      result += length2 - length1
    }
    return result
  }

  /**
   * Maps a line index in the "after" side of [ranges] back to the "before" side.
   *
   * Returns `null` when the line only exists in the "after" side, unless [approximate] is set.
   */
  fun transferLineFromAfter(ranges: List<Range>, line: Int, approximate: Boolean = false): Int? {
    if (ranges.isEmpty()) return line
    var result = line
    for (range in ranges) {
      if (line < range.start2) return result

      if (line in range.start2 until range.end2) {
        return if (approximate) range.end1 else null
      }

      val length1 = range.end1 - range.start1
      val length2 = range.end2 - range.start2
      result -= length2 - length1
    }
    return result
  }

  /**
   * Continuously reports the diff between [originalContent] and [document] to [changesCollector].
   * Suspends until canceled.
   */
  suspend fun trackDocumentDiffSync(originalContent: CharSequence, document: Document, changesCollector: (List<Range>) -> Unit): Nothing {
    val reviewHeadDocument = LineStatusTrackerBase.createVcsDocument(originalContent)
    trackDocumentDiffSync(reviewHeadDocument, document, changesCollector)
  }

  /**
   * Continuously reports the diff between [originalDocument] and [currentDocument] to [changesCollector].
   * Suspends until canceled.
   */
  suspend fun trackDocumentDiffSync(originalDocument: Document, currentDocument: Document, changesCollector: (List<Range>) -> Unit): Nothing {
    withContext(Dispatchers.EdtImmediate) {
      val documentTracker = DocumentTracker(originalDocument, currentDocument)
      val trackerHandler = object : DocumentTracker.Handler {
        override fun afterBulkRangeChange(isDirty: Boolean) {
          val trackerRanges = documentTracker.blocks.map { it.range }
          changesCollector(trackerRanges)
        }
      }

      try {
        documentTracker.addHandler(trackerHandler)
        trackerHandler.afterBulkRangeChange(true)
        awaitCancellation()
      }
      finally {
        Disposer.dispose(documentTracker)
      }
    }
  }
}
