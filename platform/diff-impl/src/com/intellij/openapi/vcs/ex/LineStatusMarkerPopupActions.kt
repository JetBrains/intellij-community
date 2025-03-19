// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.ex

import com.intellij.diff.DiffApplicationSettings
import com.intellij.diff.DiffContentFactory
import com.intellij.diff.DiffManager
import com.intellij.diff.contents.DiffContent
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.diff.util.DiffUtil
import com.intellij.icons.AllIcons
import com.intellij.ide.lightEdit.LightEditCompatible
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.diff.DiffBundle
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import java.awt.Point
import java.awt.datatransfer.StringSelection

object LineStatusMarkerPopupActions {
  @JvmStatic
  fun showDiff(tracker: LineStatusTrackerI<*>, range: Range) {
    val project = tracker.project
    val ourRange = expand(range, tracker.document, tracker.vcsDocument)
    val vcsContent = createDiffContent(project,
                                       tracker.vcsDocument,
                                       tracker.virtualFile,
                                       DiffUtil.getLinesRange(tracker.vcsDocument, ourRange.vcsLine1, ourRange.vcsLine2))
    val currentContent = createDiffContent(project,
                                           tracker.document,
                                           tracker.virtualFile,
                                           DiffUtil.getLinesRange(tracker.document, ourRange.line1, ourRange.line2))
    val request = SimpleDiffRequest(DiffBundle.message("dialog.title.diff.for.range"),
                                    vcsContent, currentContent,
                                    DiffBundle.message("diff.content.title.up.to.date"),
                                    DiffBundle.message("diff.content.title.current.range"))
    DiffManager.getInstance().showDiff(project, request)
  }

  private fun createDiffContent(project: Project?,
                                document: Document,
                                highlightFile: VirtualFile?,
                                textRange: TextRange): DiffContent {
    val content = DiffContentFactory.getInstance().create(project, document, highlightFile)
    return DiffContentFactory.getInstance().createFragment(project, content, textRange)
  }

  private fun expand(range: Range, document: Document, uDocument: Document): Range {
    val canExpandBefore = range.line1 != 0 && range.vcsLine1 != 0
    val canExpandAfter = range.line2 < DiffUtil.getLineCount(document) && range.vcsLine2 < DiffUtil.getLineCount(uDocument)
    val offset1 = range.line1 - if (canExpandBefore) 1 else 0
    val uOffset1 = range.vcsLine1 - if (canExpandBefore) 1 else 0
    val offset2 = range.line2 + if (canExpandAfter) 1 else 0
    val uOffset2 = range.vcsLine2 + if (canExpandAfter) 1 else 0
    return Range(offset1, offset2, uOffset1, uOffset2)
  }

  fun copyVcsContent(tracker: LineStatusTrackerI<*>, range: Range) {
    val content = DiffUtil.getLinesContent(tracker.vcsDocument, range.vcsLine1, range.vcsLine2).toString() + "\n"
    CopyPasteManager.getInstance().setContents(StringSelection(content))
  }


  abstract class RangeMarkerAction(private val editor: Editor,
                                   private val rangesSource: LineStatusMarkerRangesSource<*>,
                                   private val range: Range,
                                   actionId: @NonNls String?) : DumbAwareAction() {
    init {
      if (actionId != null) ActionUtil.copyFrom(this, actionId)
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun update(e: AnActionEvent) {
      val newRange = rangesSource.findRange(range)
      e.presentation.setEnabled(newRange != null && !editor.isDisposed() && isEnabled(editor, newRange))
    }

    override fun actionPerformed(e: AnActionEvent) {
      val newRange = rangesSource.findRange(range)
      if (newRange != null) actionPerformed(editor, newRange)
    }

    protected abstract fun isEnabled(editor: Editor, range: Range): Boolean
    protected abstract fun actionPerformed(editor: Editor, range: Range)
  }

  @ApiStatus.NonExtendable
  open class ShowNextChangeMarkerAction(editor: Editor,
                                        private val tracker: LineStatusTrackerI<*>,
                                        range: Range,
                                        private val controller: LineStatusMarkerRendererWithPopupController)
    : RangeMarkerAction(editor, tracker, range, "VcsShowNextChangeMarker"), LightEditCompatible {

    override fun isEnabled(editor: Editor, range: Range): Boolean = tracker.getNextRange(range.line1) != null

    override fun actionPerformed(editor: Editor, range: Range) {
      val targetRange = tracker.getNextRange(range.line1)
      if (targetRange != null) {
        controller.scrollAndShow(editor, targetRange)
      }
    }
  }

  @ApiStatus.NonExtendable
  open class ShowPrevChangeMarkerAction(editor: Editor,
                                        private val tracker: LineStatusTrackerI<*>,
                                        range: Range,
                                        private val controller: LineStatusMarkerRendererWithPopupController)
    : RangeMarkerAction(editor, tracker, range, "VcsShowPrevChangeMarker"), LightEditCompatible {

    override fun isEnabled(editor: Editor, range: Range): Boolean = tracker.getPrevRange(range.line1) != null

    override fun actionPerformed(editor: Editor, range: Range) {
      val targetRange = tracker.getPrevRange(range.line1)
      if (targetRange != null) {
        controller.scrollAndShow(editor, targetRange)
      }
    }
  }

  @ApiStatus.NonExtendable
  open class CopyLineStatusRangeAction(editor: Editor, private val tracker: LineStatusTrackerI<*>, range: Range)
    : RangeMarkerAction(editor, tracker, range, IdeActions.ACTION_COPY), LightEditCompatible {
    override fun isEnabled(editor: Editor, range: Range): Boolean = range.hasVcsLines()
    override fun actionPerformed(editor: Editor, range: Range) = copyVcsContent(tracker, range)
  }

  open class ShowLineStatusRangeDiffAction(editor: Editor, private val tracker: LineStatusTrackerI<*>, range: Range)
    : RangeMarkerAction(editor, tracker, range, "Vcs.ShowDiffChangedLines"), LightEditCompatible {
    init {
      setShortcutSet(CompositeShortcutSet(KeymapUtil.getActiveKeymapShortcuts("Vcs.ShowDiffChangedLines"),
                                          KeymapUtil.getActiveKeymapShortcuts(IdeActions.ACTION_SHOW_DIFF_COMMON)))
    }

    override fun isEnabled(editor: Editor, range: Range): Boolean = true
    override fun actionPerformed(editor: Editor, range: Range) = showDiff(tracker, range)
  }

  @ApiStatus.NonExtendable
  open class ToggleByWordDiffAction(
    private val editor: Editor,
    private val tracker: LineStatusMarkerRangesSource<*>,
    private val range: Range,
    private val mousePosition: Point?,
    private val controller: LineStatusMarkerRendererWithPopupController,
  ) : ToggleAction(DiffBundle.message("highlight.words"), null, AllIcons.Actions.Highlighting), DumbAware, LightEditCompatible {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun isSelected(e: AnActionEvent): Boolean = DiffApplicationSettings.getInstance().SHOW_LST_WORD_DIFFERENCES

    override fun setSelected(e: AnActionEvent, state: Boolean) {
      if (!tracker.isValid()) return
      DiffApplicationSettings.getInstance().SHOW_LST_WORD_DIFFERENCES = state
      controller.reopenRange(editor, range, mousePosition)
    }
  }
}
