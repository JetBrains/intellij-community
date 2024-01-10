// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.ex

import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInsight.hint.HintManagerImpl
import com.intellij.diff.util.DiffUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.diff.DefaultFlagsProvider
import com.intellij.openapi.diff.LineStatusMarkerDrawUtil
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.markup.LineMarkerRenderer
import com.intellij.openapi.editor.markup.MarkupEditorFilter
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.ex.LineStatusMarkerPopupPanel.showPopupAt
import java.awt.Graphics
import java.awt.Point
import java.awt.Rectangle
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.SwingUtilities
import kotlin.math.min

abstract class LineStatusMarkerRendererWithPopup(
  project: Project?,
  document: Document,
  protected val rangesSource: LineStatusMarkerRangesSource<*>,
  disposable: Disposable,
  editorFilter: MarkupEditorFilter? = null,
  isMain: Boolean = true
) : LineStatusMarkerRenderer(project, document, disposable, editorFilter, isMain),
    LineStatusMarkerRendererWithPopupController {

  final override fun getRanges(): List<Range>? = rangesSource.getRanges()

  override fun scrollAndShow(editor: Editor, range: Range) {
    if (!rangesSource.isValid()) return
    moveToRange(editor, range)
    showAfterScroll(editor, range)
  }

  final override fun showAfterScroll(editor: Editor, range: Range) {
    editor.getScrollingModel().runActionOnScrollingFinished(Runnable { reopenRange(editor, range, null) })
  }

  private fun showHint(editor: Editor, range: Range, e: MouseEvent) {
    val comp = e.component as JComponent // shall be EditorGutterComponent, cast is safe.
    val layeredPane = comp.rootPane.layeredPane
    val point = SwingUtilities.convertPoint(comp, (editor as EditorEx).getGutterComponentEx().width, e.y, layeredPane)
    showHintAt(editor, range, point)
    e.consume()
  }

  override fun showHintAt(editor: Editor, range: Range, mousePosition: Point?) {
    if (!rangesSource.isValid()) return
    val popupDisposable = Disposer.newDisposable(disposable)
    val popup = createPopupPanel(editor, range, mousePosition, popupDisposable)
    showPopupAt(editor, popup, mousePosition, popupDisposable)
  }

  protected abstract fun createPopupPanel(editor: Editor, range: Range, mousePosition: Point?, disposable: Disposable)
    : LineStatusMarkerPopupPanel

  final override fun reopenRange(editor: Editor, range: Range, mousePosition: Point?) {
    val newRange = rangesSource.findRange(range)
    if (newRange != null) {
      showHintAt(editor, newRange, mousePosition)
    }
    else {
      HintManagerImpl.getInstanceImpl().hideHints(HintManager.HIDE_BY_SCROLLING, false, false)
    }
  }

  final override fun createGutterMarkerRenderer(): LineMarkerRenderer = LineStatusGutterMarkerRendererWithPopup()

  protected open fun shouldPaintGutter(): Boolean = true

  protected open fun paintGutterMarkers(editor: Editor, ranges: List<Range>, g: Graphics) {
    LineStatusMarkerDrawUtil.paintDefault(editor, g, ranges, DefaultFlagsProvider.DEFAULT, 0)
  }

  private inner class LineStatusGutterMarkerRendererWithPopup : ActiveLineStatusGutterMarkerRenderer() {
    override fun getPaintedRanges(): List<Range>? = if (shouldPaintGutter()) rangesSource.getRanges().orEmpty() else null

    override fun paint(editor: Editor, g: Graphics, r: Rectangle) {
      val ranges = getPaintedRanges() ?: return
      paintGutterMarkers(editor, ranges, g)
    }

    override fun canDoAction(editor: Editor, ranges: List<Range>, e: MouseEvent): Boolean =
      LineStatusMarkerDrawUtil.isInsideMarkerArea(e)

    override fun doAction(editor: Editor, ranges: List<Range>, e: MouseEvent) {
      val range = ranges[0]
      if (ranges.size > 1) {
        scrollAndShow(editor, range)
      }
      else {
        showHint(editor, range, e)
      }
    }
  }

  companion object {
    fun moveToRange(editor: Editor, range: Range) {
      val document = editor.getDocument()

      val targetLine = if (!range.hasLines()) range.line2 else range.line2 - 1
      val line = min(targetLine, DiffUtil.getLineCount(document) - 1)

      val lastOffset = document.getLineStartOffset(line)
      editor.getCaretModel().moveToOffset(lastOffset)
      editor.getScrollingModel().scrollToCaret(ScrollType.CENTER)
    }
  }
}