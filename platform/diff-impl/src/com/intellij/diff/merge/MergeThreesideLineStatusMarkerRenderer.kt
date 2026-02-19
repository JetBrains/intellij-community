package com.intellij.diff.merge

import com.intellij.diff.util.DiffUtil
import com.intellij.diff.util.ThreeSide
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.diff.DefaultFlagsProvider
import com.intellij.openapi.diff.LineStatusMarkerDrawUtil
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.editor.markup.MarkupEditorFilter
import com.intellij.openapi.vcs.ex.LineStatusMarkerPopupActions
import com.intellij.openapi.vcs.ex.LineStatusTrackerBase
import com.intellij.openapi.vcs.ex.LineStatusTrackerMarkerRenderer
import com.intellij.openapi.vcs.ex.Range
import com.intellij.ui.scale.JBUIScale
import java.awt.Graphics
import java.awt.Point
import kotlin.math.min

internal class MergeThreesideLineStatusMarkerRenderer(private val tracker: LineStatusTrackerBase<*>, private val viewer: MergeThreesideViewer) :
  LineStatusTrackerMarkerRenderer(tracker, MarkupEditorFilter { editor: Editor? -> editor === viewer.editor }) {

  override fun scrollAndShow(editor: Editor, range: Range) {
    if (!tracker.isValid()) return
    val document = tracker.document
    val line = min(if (!range.hasLines()) range.line2 else range.line2 - 1, DiffUtil.getLineCount(document) - 1)

    val startLines = intArrayOf(
      viewer.transferPosition(ThreeSide.BASE, ThreeSide.LEFT, LogicalPosition(line, 0)).line,
      line,
      viewer.transferPosition(ThreeSide.BASE, ThreeSide.RIGHT, LogicalPosition(line, 0)).line
    )

    for (side in ThreeSide.entries) {
      DiffUtil.moveCaret(viewer.getEditor(side), side.select(startLines))
    }

    viewer.editor.getScrollingModel().scrollToCaret(ScrollType.CENTER)
    showAfterScroll(editor, range)
  }

  override fun createToolbarActions(
    editor: Editor,
    range: Range,
    mousePosition: Point?,
  ): List<AnAction> = buildList {
    add(LineStatusMarkerPopupActions.ShowPrevChangeMarkerAction(editor, tracker, range, this@MergeThreesideLineStatusMarkerRenderer))
    add(LineStatusMarkerPopupActions.ShowNextChangeMarkerAction(editor, tracker, range, this@MergeThreesideLineStatusMarkerRenderer))
    add(RollbackLineStatusRangeAction(editor, tracker, range))
    add(LineStatusMarkerPopupActions.ShowLineStatusRangeDiffAction(editor, tracker, range))
    add(LineStatusMarkerPopupActions.CopyLineStatusRangeAction(editor, tracker, range))
    add(LineStatusMarkerPopupActions.ToggleByWordDiffAction(editor, tracker, range, mousePosition, this@MergeThreesideLineStatusMarkerRenderer))
  }

  override fun paintGutterMarkers(editor: Editor, ranges: List<Range>, g: Graphics) {
    val framingBorder = JBUIScale.scale(2)
    LineStatusMarkerDrawUtil.paintDefault(editor, g, ranges, DefaultFlagsProvider.DEFAULT, framingBorder)
  }

  override fun toString(): String {
    return "MergeThreesideViewer.MyLineStatusMarkerRenderer{myTracker=$tracker}"
  }
}

internal class RollbackLineStatusRangeAction(editor: Editor, private val tracker: LineStatusTrackerBase<*>, range: Range) :
  LineStatusMarkerPopupActions.RangeMarkerAction(editor, tracker, range, IdeActions.SELECTED_CHANGES_ROLLBACK) {
  override fun isEnabled(editor: Editor, range: Range): Boolean {
    return true
  }

  override fun actionPerformed(editor: Editor, range: Range) {
    DiffUtil.moveCaretToLineRangeIfNeeded(editor, range.line1, range.line2)
    tracker.rollbackChanges(range)
  }
}