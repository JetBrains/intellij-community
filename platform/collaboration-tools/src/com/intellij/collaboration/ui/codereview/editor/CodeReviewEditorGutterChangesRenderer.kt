// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.editor

import com.intellij.collaboration.async.launchNow
import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.diff.comparison.ComparisonManager
import com.intellij.diff.comparison.ComparisonPolicy
import com.intellij.diff.util.DiffUtil
import com.intellij.diff.util.LineRange
import com.intellij.icons.AllIcons
import com.intellij.ide.lightEdit.LightEditCompatible
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diff.DefaultFlagsProvider
import com.intellij.openapi.diff.LineStatusMarkerColorScheme
import com.intellij.openapi.diff.LineStatusMarkerDrawUtil
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.observable.util.whenDisposed
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.util.BackgroundTaskUtil
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.ex.LineStatusMarkerPopupActions
import com.intellij.openapi.vcs.ex.LineStatusMarkerPopupPanel
import com.intellij.openapi.vcs.ex.LineStatusMarkerRendererWithPopup
import com.intellij.openapi.vcs.ex.Range
import com.intellij.ui.EditorTextField
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus
import java.awt.Color
import java.awt.Graphics
import java.awt.Point
import java.awt.datatransfer.StringSelection

/**
 * Draws and handles review changes markers in gutter
 */
@ApiStatus.NonExtendable
open class CodeReviewEditorGutterChangesRenderer(
  protected val model: CodeReviewEditorGutterActionableChangesModel,
  protected val editor: Editor,
  disposable: Disposable,
  private val lineStatusMarkerColorScheme: LineStatusMarkerColorScheme = ReviewInEditorUtil.REVIEW_STATUS_MARKER_COLOR_SCHEME,
) : LineStatusMarkerRendererWithPopup(editor.project, editor.document, model, disposable, { it === editor }) {

  override fun paintGutterMarkers(editor: Editor, ranges: List<Range>, g: Graphics) {
    LineStatusMarkerDrawUtil.paintDefault(editor, g, ranges, DefaultFlagsProvider.DEFAULT,
                                          lineStatusMarkerColorScheme, 0)
  }

  override fun createErrorStripeTextAttributes(diffType: Byte): TextAttributes = ReviewChangesTextAttributes()

  private inner class ReviewChangesTextAttributes : TextAttributes() {
    override fun getErrorStripeColor(): Color = ReviewInEditorUtil.REVIEW_CHANGES_STATUS_COLOR
  }


  override fun createPopupPanel(editor: Editor,
                                range: Range,
                                mousePosition: Point?,
                                disposable: Disposable): LineStatusMarkerPopupPanel {
    val vcsContent = model.getBaseContent(LineRange(range.vcsLine1, range.vcsLine2))?.removeSuffix("\n")

    val editorComponent = if (vcsContent != null) {
      val popupEditor = createPopupEditor(project, editor, vcsContent, disposable)
      showLineDiff(editor, popupEditor, range, vcsContent, disposable)
      LineStatusMarkerPopupPanel.createEditorComponent(editor, popupEditor.component)
    }
    else {
      null
    }

    val actions = createActions(range)

    val toolbar = LineStatusMarkerPopupPanel.buildToolbar(editor, actions, disposable)
    return LineStatusMarkerPopupPanel.create(editor, toolbar, editorComponent, null)
  }

  protected open fun createActions(range: Range): List<AnAction> {
    return listOf(
      ShowPrevChangeMarkerAction(range),
      ShowNextChangeMarkerAction(range),
      CopyLineStatusRangeAction(range),
      ShowDiffAction(range),
      ToggleByWordDiffAction()
    )
  }

  private fun createPopupEditor(project: Project?, mainEditor: Editor, vcsContent: String, disposable: Disposable): Editor {
    val factory = EditorFactory.getInstance()
    val editor = factory.createViewer(factory.createDocument(vcsContent), project, EditorKind.DIFF) as EditorEx

    ReadAction.run<RuntimeException> {
      with(editor) {
        setCaretEnabled(false)
        getContentComponent().setFocusCycleRoot(false)

        setRendererMode(true)
        EditorTextField.setupTextFieldEditor(this)
        setVerticalScrollbarVisible(true)
        setHorizontalScrollbarVisible(true)
        setBorder(null)

        with(getSettings()) {
          setUseSoftWraps(false)
          setTabSize(mainEditor.getSettings().getTabSize(project))
          setUseTabCharacter(mainEditor.getSettings().isUseTabCharacter(project))
        }
        setColorsScheme(mainEditor.getColorsScheme())
        setBackgroundColor(LineStatusMarkerPopupPanel.getEditorBackgroundColor(mainEditor))

        getSelectionModel().removeSelection()
      }
    }
    disposable.whenDisposed {
      factory.releaseEditor(editor)
    }
    return editor
  }

  private fun showLineDiff(editor: Editor,
                           popupEditor: Editor,
                           range: Range, vcsContent: CharSequence,
                           disposable: Disposable) {
    var highlightersDisposable: Disposable? = null
    fun update(show: Boolean) {
      if (show && highlightersDisposable == null) {
        val currentContent = DiffUtil.getLinesContent(editor.document, range.line1, range.line2)
        if (currentContent.isEmpty()) return

        val newDisposable = Disposer.newDisposable().also {
          Disposer.register(disposable, it)
        }
        highlightersDisposable = newDisposable

        val lineDiff = BackgroundTaskUtil.tryComputeFast({ indicator: ProgressIndicator? ->
                                                           ComparisonManager.getInstance().compareLines(vcsContent, currentContent,
                                                                                                        ComparisonPolicy.DEFAULT,
                                                                                                        indicator!!)
                                                         }, 200)
        if (lineDiff == null) return
        LineStatusMarkerPopupPanel.installMasterEditorWordHighlighters(editor, range.line1, range.line2, lineDiff, newDisposable)
        LineStatusMarkerPopupPanel.installEditorDiffHighlighters(popupEditor, lineDiff).also {
          newDisposable.whenDisposed {
            it.forEach(RangeHighlighter::dispose)
          }
        }
      }
      else {
        highlightersDisposable?.let(Disposer::dispose)
        highlightersDisposable = null
      }
    }

    model.addDiffHighlightListener(disposable) {
      update(model.shouldHighlightDiffRanges)
    }
    update(model.shouldHighlightDiffRanges)
  }

  protected inner class ShowNextChangeMarkerAction(range: Range)
    : LineStatusMarkerPopupActions.RangeMarkerAction(editor, rangesSource, range, "VcsShowNextChangeMarker"), LightEditCompatible {

    override fun isEnabled(editor: Editor, range: Range): Boolean = getNextRange(range.line1) != null

    override fun actionPerformed(editor: Editor, range: Range) {
      val targetRange = getNextRange(range.line1)
      if (targetRange != null) {
        scrollAndShow(editor, targetRange)
      }
    }

    private fun getNextRange(line: Int): Range? {
      val ranges = rangesSource.getRanges() ?: return null
      return getNextRange(ranges, line)
    }
  }

  protected inner class ShowPrevChangeMarkerAction(range: Range)
    : LineStatusMarkerPopupActions.RangeMarkerAction(editor, rangesSource, range, "VcsShowPrevChangeMarker"), LightEditCompatible {

    override fun isEnabled(editor: Editor, range: Range): Boolean = getPrevRange(range.line1) != null

    override fun actionPerformed(editor: Editor, range: Range) {
      val targetRange = getPrevRange(range.line1)
      if (targetRange != null) {
        scrollAndShow(editor, targetRange)
      }
    }

    private fun getPrevRange(line: Int): Range? {
      val ranges = rangesSource.getRanges()?.reversed() ?: return null
      return getNextRange(ranges, line)
    }
  }

  protected inner class CopyLineStatusRangeAction(range: Range)
    : LineStatusMarkerPopupActions.RangeMarkerAction(editor, rangesSource, range, IdeActions.ACTION_COPY), LightEditCompatible {
    override fun isEnabled(editor: Editor, range: Range): Boolean = range.hasVcsLines()
    override fun actionPerformed(editor: Editor, range: Range) {
      val content = model.getBaseContent(LineRange(range.vcsLine1, range.vcsLine2))
      CopyPasteManager.getInstance().setContents(StringSelection(content))
    }
  }

  protected inner class ShowDiffAction(range: Range)
    : LineStatusMarkerPopupActions.RangeMarkerAction(editor, rangesSource, range, "Vcs.ShowDiffChangedLines"), LightEditCompatible {
    init {
      setShortcutSet(CompositeShortcutSet(KeymapUtil.getActiveKeymapShortcuts("Vcs.ShowDiffChangedLines"),
                                          KeymapUtil.getActiveKeymapShortcuts(IdeActions.ACTION_SHOW_DIFF_COMMON)))
      with(templatePresentation) {
        text = CollaborationToolsBundle.message("review.diff.action.show.text")
        description = CollaborationToolsBundle.message("review.diff.action.show.description")
      }
    }

    override fun isEnabled(editor: Editor, range: Range): Boolean = true
    override fun actionPerformed(editor: Editor, range: Range) {
      model.showDiff(range.line1)
    }
  }

  protected inner class ToggleByWordDiffAction
    : ToggleAction(CollaborationToolsBundle.message("review.editor.action.highlight.lines.text"), null, AllIcons.Actions.Highlighting),
      DumbAware, LightEditCompatible {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun isSelected(e: AnActionEvent): Boolean = model.shouldHighlightDiffRanges

    override fun setSelected(e: AnActionEvent, state: Boolean) {
      model.shouldHighlightDiffRanges = state
    }
  }

  companion object {
    @ApiStatus.ScheduledForRemoval
    @Deprecated("Use a suspending function", ReplaceWith("cs.launch { render(model, editor) }"))
    fun setupIn(cs: CoroutineScope, model: CodeReviewEditorGutterActionableChangesModel, editor: Editor) {
      cs.launchNow { render(model, editor) }
    }

    suspend fun render(model: CodeReviewEditorGutterActionableChangesModel, editor: Editor) : Nothing {
      withContext(Dispatchers.Main + CoroutineName("Editor gutter code review changes renderer")) {
        val disposable = Disposer.newDisposable("Editor code review changes renderer disposable")
        editor.putUserData(CodeReviewEditorGutterActionableChangesModel.KEY, model)
        try {
          val renderer = CodeReviewEditorGutterChangesRenderer(model, editor, disposable, ReviewInEditorUtil.REVIEW_STATUS_MARKER_COLOR_SCHEME)
          model.reviewRanges.collect {
            renderer.scheduleUpdate()
          }
        }
        finally {
          withContext(NonCancellable) {
            Disposer.dispose(disposable)
            editor.putUserData(CodeReviewEditorGutterActionableChangesModel.KEY, null)
          }
        }
      }
    }
  }
}

private fun getNextRange(ranges: List<Range>, line: Int): Range? {
  var found = false
  for (range in ranges) {
    if (DiffUtil.isSelectedByLine(line, range.line1, range.line2)) {
      found = true
    }
    else if (found) {
      return range
    }
  }
  return null
}