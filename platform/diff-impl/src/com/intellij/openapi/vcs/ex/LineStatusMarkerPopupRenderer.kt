// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.ex

import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInsight.hint.HintManagerImpl
import com.intellij.diff.DiffApplicationSettings
import com.intellij.diff.comparison.ByWord
import com.intellij.diff.comparison.ComparisonPolicy
import com.intellij.diff.util.DiffUtil
import com.intellij.icons.AllIcons
import com.intellij.ide.lightEdit.LightEditCompatible
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.diff.DefaultFlagsProvider
import com.intellij.openapi.diff.DiffBundle
import com.intellij.openapi.diff.LineStatusMarkerDrawUtil
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.markup.LineMarkerRenderer
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.progress.util.BackgroundTaskUtil
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.Disposer
import com.intellij.ui.EditorTextField
import org.jetbrains.annotations.NonNls
import java.awt.Graphics
import java.awt.Point
import java.awt.Rectangle
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.SwingUtilities
import kotlin.math.min

abstract class LineStatusMarkerPopupRenderer(protected open val tracker: LineStatusTrackerI<*>)
  : LineStatusMarkerRenderer(tracker.project, tracker.document, tracker.disposable) {

  override fun getRanges(): List<Range>? = tracker.getRanges()

  /**
   * @return true if gutter markers should be painted, false otherwise
   */
  protected open fun shouldPaintGutter(): Boolean = true

  final override fun createGutterMarkerRenderer(): LineMarkerRenderer = object : ActiveLineStatusGutterMarkerRenderer() {
    override fun getPaintedRanges(): List<Range>? {
      if (!shouldPaintGutter()) return null
      return getRanges().orEmpty()
    }

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

  protected open fun paintGutterMarkers(editor: Editor, ranges: List<Range>, g: Graphics) {
    LineStatusMarkerDrawUtil.paintDefault(editor, g, ranges, DefaultFlagsProvider.DEFAULT, 0)
  }

  open fun scrollAndShow(editor: Editor, range: Range) {
    if (!tracker.isValid()) return
    moveToRange(editor, range)
    showAfterScroll(editor, range)
  }

  fun showAfterScroll(editor: Editor, range: Range) {
    editor.getScrollingModel().runActionOnScrollingFinished(Runnable { reopenRange(editor, range, null) })
  }

  private fun showHint(editor: Editor, range: Range, e: MouseEvent) {
    val comp = e.component as JComponent // shall be EditorGutterComponent, cast is safe.
    val layeredPane = comp.rootPane.layeredPane
    val point = SwingUtilities.convertPoint(comp, (editor as EditorEx).getGutterComponentEx().width, e.y, layeredPane)
    showHintAt(editor, range, point)
    e.consume()
  }

  open fun showHintAt(editor: Editor, range: Range, mousePosition: Point?) {
    if (!tracker.isValid()) return
    val disposable = Disposer.newDisposable()
    var editorComponent: JComponent? = null
    if (range.hasVcsLines()) {
      val content = LineStatusMarkerPopupActions.getVcsContent(tracker, range).toString()
      val textField = LineStatusMarkerPopupPanel.createTextField(editor, content)
      LineStatusMarkerPopupPanel.installBaseEditorSyntaxHighlighters(tracker.project, textField, tracker.vcsDocument,
                                                                     LineStatusMarkerPopupActions.getVcsTextRange(tracker, range),
                                                                     fileType)
      installWordDiff(editor, textField, range, disposable)
      editorComponent = LineStatusMarkerPopupPanel.createEditorComponent(editor, textField)
    }
    val actions = createToolbarActions(editor, range, mousePosition)
    val toolbar = LineStatusMarkerPopupPanel.buildToolbar(editor, actions, disposable)
    val additionalInfoPanel = createAdditionalInfoPanel(editor, range, mousePosition, disposable)
    LineStatusMarkerPopupPanel.showPopupAt(editor, toolbar, editorComponent, additionalInfoPanel, mousePosition, disposable, null)
  }

  protected val fileType: FileType
    get() {
      val virtualFile = tracker.virtualFile
      return virtualFile?.fileType ?: PlainTextFileType.INSTANCE
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

    val vcsContent = LineStatusMarkerPopupActions.getVcsContent(tracker, range)
    val currentContent = LineStatusMarkerPopupActions.getCurrentContent(tracker, range)
    val wordDiff = BackgroundTaskUtil
                     .tryComputeFast({ indicator -> ByWord.compare(vcsContent, currentContent, ComparisonPolicy.DEFAULT, indicator) }, 200)
                   ?: return
    LineStatusMarkerPopupPanel.installMasterEditorWordHighlighters(editor, range.line1, range.line2, wordDiff, disposable)
    LineStatusMarkerPopupPanel.installPopupEditorWordHighlighters(textField, wordDiff)
  }

  protected fun reopenRange(editor: Editor, range: Range, mousePosition: Point?) {
    val newRange = tracker.findRange(range)
    if (newRange != null) {
      showHintAt(editor, newRange, mousePosition)
    }
    else {
      HintManagerImpl.getInstanceImpl().hideHints(HintManager.HIDE_BY_SCROLLING, false, false)
    }
  }

  abstract inner class RangeMarkerAction(private val editor: Editor,
                                         private val range: Range,
                                         actionId: @NonNls String?) : DumbAwareAction() {
    init {
      if (actionId != null) ActionUtil.copyFrom(this, actionId)
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun update(e: AnActionEvent) {
      val newRange = tracker.findRange(range)
      e.presentation.setEnabled(newRange != null && !editor.isDisposed() && isEnabled(editor, newRange))
    }

    override fun actionPerformed(e: AnActionEvent) {
      val newRange = tracker.findRange(range)
      if (newRange != null) actionPerformed(editor, newRange)
    }

    protected abstract fun isEnabled(editor: Editor, range: Range): Boolean
    protected abstract fun actionPerformed(editor: Editor, range: Range)
  }

  inner class ShowNextChangeMarkerAction(editor: Editor, range: Range)
    : RangeMarkerAction(editor, range, "VcsShowNextChangeMarker"), LightEditCompatible {
    override fun isEnabled(editor: Editor, range: Range): Boolean = tracker.getNextRange(range.line1) != null

    override fun actionPerformed(editor: Editor, range: Range) {
      val targetRange = tracker.getNextRange(range.line1)
      if (targetRange != null) {
        scrollAndShow(editor, targetRange)
      }
    }
  }

  inner class ShowPrevChangeMarkerAction(editor: Editor, range: Range)
    : RangeMarkerAction(editor, range, "VcsShowPrevChangeMarker"), LightEditCompatible {
    override fun isEnabled(editor: Editor, range: Range): Boolean = tracker.getPrevRange(range.line1) != null

    override fun actionPerformed(editor: Editor, range: Range) {
      val targetRange = tracker.getPrevRange(range.line1)
      if (targetRange != null) {
        scrollAndShow(editor, targetRange)
      }
    }
  }

  inner class CopyLineStatusRangeAction(editor: Editor, range: Range)
    : RangeMarkerAction(editor, range, IdeActions.ACTION_COPY), LightEditCompatible {
    override fun isEnabled(editor: Editor, range: Range): Boolean = range.hasVcsLines()
    override fun actionPerformed(editor: Editor, range: Range) = LineStatusMarkerPopupActions.copyVcsContent(tracker, range)
  }

  inner class ShowLineStatusRangeDiffAction(editor: Editor, range: Range)
    : RangeMarkerAction(editor, range, "Vcs.ShowDiffChangedLines"), LightEditCompatible {
    init {
      setShortcutSet(CompositeShortcutSet(KeymapUtil.getActiveKeymapShortcuts("Vcs.ShowDiffChangedLines"),
                                          KeymapUtil.getActiveKeymapShortcuts(IdeActions.ACTION_SHOW_DIFF_COMMON)))
    }

    override fun isEnabled(editor: Editor, range: Range): Boolean = true
    override fun actionPerformed(editor: Editor, range: Range) = LineStatusMarkerPopupActions.showDiff(tracker, range)
  }

  inner class ToggleByWordDiffAction(private val myEditor: Editor,
                                     private val myRange: Range,
                                     private val myMousePosition: Point?)
    : ToggleAction(DiffBundle.message("highlight.words"), null, AllIcons.Actions.Highlighting), DumbAware, LightEditCompatible {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun isSelected(e: AnActionEvent): Boolean = DiffApplicationSettings.getInstance().SHOW_LST_WORD_DIFFERENCES

    override fun setSelected(e: AnActionEvent, state: Boolean) {
      if (!tracker.isValid()) return
      DiffApplicationSettings.getInstance().SHOW_LST_WORD_DIFFERENCES = state
      reopenRange(myEditor, myRange, myMousePosition)
    }
  }
}

private fun moveToRange(editor: Editor, range: Range) {
  val document = editor.getDocument()

  val targetLine = if (!range.hasLines()) range.line2 else range.line2 - 1
  val line = min(targetLine, DiffUtil.getLineCount(document) - 1)

  val lastOffset = document.getLineStartOffset(line)
  editor.getCaretModel().moveToOffset(lastOffset)
  editor.getScrollingModel().scrollToCaret(ScrollType.CENTER)
}
