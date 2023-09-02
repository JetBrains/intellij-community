// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.ex

import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInsight.hint.HintManagerImpl
import com.intellij.diff.DiffApplicationSettings
import com.intellij.diff.comparison.ByWord
import com.intellij.diff.comparison.ComparisonPolicy
import com.intellij.diff.util.DiffUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.diff.DefaultFlagsProvider
import com.intellij.openapi.diff.LineStatusMarkerDrawUtil
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.markup.LineMarkerRenderer
import com.intellij.openapi.progress.util.BackgroundTaskUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorTextField
import org.jetbrains.annotations.ApiStatus
import java.awt.Graphics
import java.awt.Point
import java.awt.Rectangle
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.SwingUtilities
import kotlin.math.min

open class LineStatusMarkerRendererWithPopup(
  project: Project?,
  protected val vcsDocument: Document,
  document: Document,
  protected val virtualFile: VirtualFile?,
  protected val rangesSource: LineStatusMarkerRangesSource<*>,
  disposable: Disposable
) : LineStatusMarkerRenderer(project, document, disposable),
    LineStatusMarkerRendererWithPopupController {

  // Convenience constructor
  // Better convert this to an extension or a method
  @ApiStatus.Internal
  constructor(tracker: LineStatusTrackerI<*>)
    : this(tracker.project, tracker.vcsDocument, tracker.document, tracker.virtualFile, tracker, tracker.disposable)

  override fun getRanges(): List<Range>? = rangesSource.getRanges()

  override fun scrollAndShow(editor: Editor, range: Range) {
    if (!rangesSource.isValid()) return
    moveToRange(editor, range)
    showAfterScroll(editor, range)
  }

  override fun showAfterScroll(editor: Editor, range: Range) {
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
    val disposable = Disposer.newDisposable()
    var editorComponent: JComponent? = null
    if (range.hasVcsLines()) {
      editorComponent = createVcsContentComponent(range, editor, disposable)
    }
    val actions = createToolbarActions(editor, range, mousePosition)
    val toolbar = LineStatusMarkerPopupPanel.buildToolbar(editor, actions, disposable)
    val additionalInfoPanel = createAdditionalInfoPanel(editor, range, mousePosition, disposable)
    LineStatusMarkerPopupPanel.showPopupAt(editor, toolbar, editorComponent, additionalInfoPanel, mousePosition, disposable, null)
  }

  private fun createVcsContentComponent(range: Range, editor: Editor, disposable: Disposable): JComponent {
    val vcsRange = DiffUtil.getLinesRange(vcsDocument, range.vcsLine1, range.vcsLine2)
    val vcsContent = DiffUtil.getLinesContent(vcsDocument, range.vcsLine1, range.vcsLine2).toString()
    val textField = LineStatusMarkerPopupPanel.createTextField(editor, vcsContent)
    LineStatusMarkerPopupPanel.installBaseEditorSyntaxHighlighters(project, textField, vcsDocument, vcsRange, virtualFile?.fileType)
    installWordDiff(editor, textField, range, disposable)
    return LineStatusMarkerPopupPanel.createEditorComponent(editor, textField)
  }

  protected open fun createToolbarActions(editor: Editor, range: Range, mousePosition: Point?): List<AnAction> = emptyList()

  protected open fun createAdditionalInfoPanel(editor: Editor,
                                               range: Range,
                                               mousePosition: Point?,
                                               disposable: Disposable): JComponent? = null

  private fun installWordDiff(editor: Editor,
                              textField: EditorTextField,
                              range: Range,
                              disposable: Disposable) {
    if (!DiffApplicationSettings.getInstance().SHOW_LST_WORD_DIFFERENCES) return
    if (!range.hasLines() || !range.hasVcsLines()) return

    val vcsContent = DiffUtil.getLinesContent(vcsDocument, range.vcsLine1, range.vcsLine2)
    val currentContent = DiffUtil.getLinesContent(document, range.line1, range.line2)
    val wordDiff = BackgroundTaskUtil.tryComputeFast(
      { indicator -> ByWord.compare(vcsContent, currentContent, ComparisonPolicy.DEFAULT, indicator) }, 200)
                   ?: return
    LineStatusMarkerPopupPanel.installMasterEditorWordHighlighters(editor, range.line1, range.line2, wordDiff, disposable)
    LineStatusMarkerPopupPanel.installPopupEditorWordHighlighters(textField, wordDiff)
  }

  override fun reopenRange(editor: Editor, range: Range, mousePosition: Point?) {
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
    override fun getPaintedRanges(): List<Range>? = rangesSource.getRanges().orEmpty()

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