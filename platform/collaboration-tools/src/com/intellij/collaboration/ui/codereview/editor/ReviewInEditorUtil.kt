// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.editor

import com.intellij.collaboration.ui.codereview.editor.action.CodeReviewInEditorToolbarActionGroup
import com.intellij.diff.util.DiffUtil
import com.intellij.diff.util.Range
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.Constraints
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.EdtImmediate
import com.intellij.openapi.application.UI
import com.intellij.openapi.diff.LineStatusMarkerColorScheme
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.EditorMarkupModel
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.ex.DocumentTracker
import com.intellij.openapi.vcs.ex.LineStatusTrackerBase
import com.intellij.openapi.vcs.ex.LstRange
import com.intellij.ui.JBColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.awt.Color

object ReviewInEditorUtil {
  val REVIEW_CHANGES_STATUS_COLOR: JBColor =
    JBColor.namedColor("Review.Editor.Line.Status.Marker", JBColor(0xF8A0DF, 0x8A4175))

  val REVIEW_STATUS_MARKER_COLOR_SCHEME: LineStatusMarkerColorScheme =
    object : LineStatusMarkerColorScheme() {
      override fun getColor(editor: Editor, type: Byte): Color = REVIEW_CHANGES_STATUS_COLOR
      override fun getIgnoredBorderColor(editor: Editor, type: Byte): Color = REVIEW_CHANGES_STATUS_COLOR
      override fun getErrorStripeColor(type: Byte): Color = REVIEW_CHANGES_STATUS_COLOR
    }

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

  suspend fun trackDocumentDiffSync(originalContent: CharSequence, document: Document, changesCollector: (List<Range>) -> Unit): Nothing {
    val reviewHeadDocument = LineStatusTrackerBase.createVcsDocument(originalContent)
    trackDocumentDiffSync(reviewHeadDocument, document, changesCollector)
  }

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

  /**
   * Sets up an inspection widget action group for review in editor
   * Suspends until canceled
   *
   * @throws IllegalStateException when the actions were not set up
   */
  suspend fun showReviewToolbar(vm: CodeReviewInEditorViewModel, editor: Editor): Nothing {
    showReviewToolbarWithActions(vm, editor)
  }

  suspend fun showReviewToolbarWithActions(vm: CodeReviewInEditorViewModel, editor: Editor, vararg additionalActions: AnAction): Nothing {
    val toolbarActionGroup = withContext(Dispatchers.UI) {
      DefaultActionGroup(
        *additionalActions,
        CodeReviewInEditorToolbarActionGroup(vm),
        Separator.getInstance()
      )
    }

    showInspectionWidgetAction(editor, toolbarActionGroup)
  }

  /**
   * This is a very special case for GitLab plugin to show on a file with an empty diff
   */
  @ApiStatus.Internal
  suspend fun showReviewToolbarWithWarning(
    vm: CodeReviewInEditorViewModel, editor: Editor,
    vararg additionalActions: AnAction,
    warningSupplier: () -> @Nls String,
  ): Nothing {
    val toolbarActionGroup = withContext(Dispatchers.UI) {
      DefaultActionGroup(
        *additionalActions,
        CodeReviewInEditorToolbarActionGroup(vm, warningSupplier),
        Separator.getInstance()
      )
    }

    showInspectionWidgetAction(editor, toolbarActionGroup)
  }

  fun isLastBlankLine(document: Document, lineIdx: Int): Boolean {
    val lineCount = DiffUtil.getLineCount(document)
    if (lineIdx != lineCount - 1) return false
    val start = document.getLineStartOffset(lineIdx)
    val end = document.getLineEndOffset(lineIdx)
    return start == end
  }

  // Awaits cancellation indefinitely until scope is cancelled
  private suspend fun showInspectionWidgetAction(editor: Editor, action: AnAction): Nothing {
    withContext(Dispatchers.EDT) {
      val editorMarkupModel = editor.markupModel as? EditorMarkupModel
      if (editorMarkupModel == null) {
        error("Editor markup model is not available")
      }
      editorMarkupModel.addInspectionWidgetAction(action, Constraints.FIRST)
      try {
        awaitCancellation()
      }
      finally {
        editorMarkupModel.removeInspectionWidgetAction(action)
      }
    }
  }
}

fun Range.asLst(): LstRange = LstRange(start2, end2, start1, end1)