// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.editor

import com.intellij.collaboration.ui.codereview.editor.action.CodeReviewInEditorToolbarActionGroup
import com.intellij.diff.util.Range
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.Constraints
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.UI
import com.intellij.openapi.diff.LineStatusMarkerColorScheme
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.ColorKey
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.ex.EditorMarkupModel
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.vcs.ex.LstRange
import com.intellij.ui.JBColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.awt.Color

object ReviewInEditorUtil {

  internal val REVIEW_CHANGED_LINES_COLOR: ColorKey = ColorKey.createColorKey("REVIEW_CHANGED_LINES_COLOR")

  val REVIEW_CHANGES_STATUS_COLOR: JBColor =
    JBColor.namedColor("Review.Editor.Line.Status.Marker", JBColor(0xF8A0DF, 0x8A4175))

  val REVIEW_STATUS_MARKER_COLOR_SCHEME: LineStatusMarkerColorScheme =
    object : LineStatusMarkerColorScheme() {
      override fun getColor(editor: Editor, type: Byte): Color {
        return editor.colorsScheme.getColor(REVIEW_CHANGED_LINES_COLOR) ?: REVIEW_CHANGES_STATUS_COLOR
      }
      override fun getIgnoredBorderColor(editor: Editor, type: Byte): Color {
        return editor.colorsScheme.getColor(REVIEW_CHANGED_LINES_COLOR) ?: REVIEW_CHANGES_STATUS_COLOR
      }
      override fun getErrorStripeColor(type: Byte): Color {
        return EditorColorsManager.getInstance().getGlobalScheme().getColor(REVIEW_CHANGED_LINES_COLOR)
               ?: REVIEW_CHANGES_STATUS_COLOR
      }
    }

  internal fun getReviewChangesTextAttribute(lineStatusMarkerColorScheme: LineStatusMarkerColorScheme): TextAttributes = object : TextAttributes() {
    override fun getErrorStripeColor(): Color = lineStatusMarkerColorScheme.getErrorStripeColor(0) ?: REVIEW_CHANGES_STATUS_COLOR
  }

  fun transferLineToAfter(ranges: List<Range>, line: Int): Int =
    CodeReviewEditorDocumentUtil.transferLineToAfter(ranges, line)

  fun transferLineFromAfter(ranges: List<Range>, line: Int, approximate: Boolean = false): Int? =
    CodeReviewEditorDocumentUtil.transferLineFromAfter(ranges, line, approximate)

  suspend fun trackDocumentDiffSync(originalContent: CharSequence, document: Document, changesCollector: (List<Range>) -> Unit): Nothing =
    CodeReviewEditorDocumentUtil.trackDocumentDiffSync(originalContent, document, changesCollector)

  suspend fun trackDocumentDiffSync(originalDocument: Document, currentDocument: Document, changesCollector: (List<Range>) -> Unit): Nothing =
    CodeReviewEditorDocumentUtil.trackDocumentDiffSync(originalDocument, currentDocument, changesCollector)

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
    return CodeReviewEditorDocumentUtil.isLastBlankLine(document, lineIdx)
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