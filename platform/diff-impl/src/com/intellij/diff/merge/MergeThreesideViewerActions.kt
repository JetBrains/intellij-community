// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.merge

import com.intellij.diff.util.DiffUtil
import com.intellij.diff.util.Side
import com.intellij.diff.util.ThreeSide
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.ex.ActionUtil.copyFrom
import com.intellij.openapi.diff.DiffBundle
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.Condition
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.concurrency.annotations.RequiresWriteLock
import org.jetbrains.annotations.Nls
import java.util.*

internal abstract class ApplySelectedChangesActionBase protected constructor(
  protected val viewer: MergeThreesideViewer
) : AnAction(), DumbAware {
  final override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  final override fun update(e: AnActionEvent) {
    if (DiffUtil.isFromShortcut(e)) {
      // consume shortcut even if there is nothing to do - avoid calling some other action
      e.presentation.setEnabledAndVisible(true)
      return
    }

    val presentation = e.presentation
    val editor = e.getData(CommonDataKeys.EDITOR)

    val side = viewer.getEditorSide(editor)
    if (side == null) {
      presentation.setEnabledAndVisible(false)
      return
    }

    if (!isVisible(side)) {
      presentation.setEnabledAndVisible(false)
      return
    }

    presentation.setText(getText(side))

    presentation.setEnabledAndVisible(isSomeChangeSelected(side) && !viewer.isExternalOperationInProgress)
  }

  final override fun actionPerformed(e: AnActionEvent) {
    val editor = e.getData(CommonDataKeys.EDITOR) ?: return
    val side = viewer.getEditorSide(editor) ?: return

    val selectedChanges = getSelectedChanges(side)
    if (selectedChanges.isEmpty()) return

    val title = DiffBundle.message("message.do.in.merge.command", e.presentation.text)
    viewer.executeMergeCommand(title, selectedChanges.size > 1, selectedChanges, Runnable { apply(side, selectedChanges) })
  }

  @RequiresWriteLock
  protected abstract fun apply(side: ThreeSide, changes: List<TextMergeChange>)

  fun isSomeChangeSelected(side: ThreeSide): Boolean {
    val editor = viewer.getEditor(side)
    return DiffUtil.isSomeRangeSelected(editor, Condition { lines: BitSet ->
      viewer.allChanges.any { change: TextMergeChange ->
        isChangeSelected(change, lines, side)
      }
    })
  }

  @RequiresEdt
  protected open fun getSelectedChanges(side: ThreeSide): List<TextMergeChange> {
    val editor = viewer.getEditor(side)
    val lines = DiffUtil.getSelectedLines(editor)
    return viewer.changes.filter { change: TextMergeChange ->
      isChangeSelected(change, lines, side)
    }
  }

  protected fun isChangeSelected(change: TextMergeChange, lines: BitSet, side: ThreeSide): Boolean {
    if (!isEnabled(change)) return false
    val line1 = change.getStartLine(side)
    val line2 = change.getEndLine(side)
    return DiffUtil.isSelectedByLine(lines, line1, line2)
  }

  protected abstract fun getText(side: ThreeSide): @Nls String?

  protected abstract fun isVisible(side: ThreeSide): Boolean

  protected abstract fun isEnabled(change: TextMergeChange): Boolean
}

internal class IgnoreSelectedChangesSideAction(
  viewer: MergeThreesideViewer,
  private val side: Side,
) : ApplySelectedChangesActionBase(viewer) {
  init {
    copyFrom(this, side.select("Diff.IgnoreLeftSide", "Diff.IgnoreRightSide"))
  }

  override fun apply(side: ThreeSide, changes: List<TextMergeChange>) {
    for (change in changes) {
      viewer.model.ignoreChange(change.index, this@IgnoreSelectedChangesSideAction.side, false)
    }
  }

  override fun getText(side: ThreeSide): String = DiffBundle.message("action.presentation.merge.ignore.text")

  override fun isVisible(side: ThreeSide): Boolean {
    return side == this.side.select(ThreeSide.LEFT, ThreeSide.RIGHT)
  }

  override fun isEnabled(change: TextMergeChange): Boolean = !change.isResolved(side)
}

internal class IgnoreSelectedChangesAction(viewer: MergeThreesideViewer) : ApplySelectedChangesActionBase(viewer) {
  init {
    getTemplatePresentation().setIcon(AllIcons.Diff.Remove)
  }

  override fun getText(side: ThreeSide): String = DiffBundle.message("action.presentation.merge.ignore.text")

  override fun isVisible(side: ThreeSide): Boolean = side == ThreeSide.BASE

  override fun isEnabled(change: TextMergeChange): Boolean = !change.isResolved

  override fun apply(side: ThreeSide, changes: List<TextMergeChange>) {
    for (change in changes) {
      viewer.model.markChangeResolved(change.index)
    }
  }
}

internal class ResetResolvedChangeAction(viewer: MergeThreesideViewer) : ApplySelectedChangesActionBase(viewer) {
  init {
    getTemplatePresentation().setIcon(AllIcons.Diff.Revert)
  }

  override fun apply(side: ThreeSide, changes: List<TextMergeChange>) {
    for (change in changes) {
      viewer.model.resetResolvedChange(change.index)
    }
  }

  override fun getSelectedChanges(side: ThreeSide): List<TextMergeChange> {
    val editor = viewer.getEditor(side)
    val lines = DiffUtil.getSelectedLines(editor)
    return viewer.allChanges.filter { change: TextMergeChange ->
      isChangeSelected(change, lines, side)
    }
  }

  override fun getText(side: ThreeSide): @Nls String = DiffBundle.message("action.presentation.diff.revert.text")

  override fun isVisible(side: ThreeSide): Boolean = true

  override fun isEnabled(change: TextMergeChange): Boolean = change.isResolvedWithAI
}

internal class ApplySelectedChangesAction(
  viewer: MergeThreesideViewer,
  private val side: Side,
) : ApplySelectedChangesActionBase(viewer) {
  init {
    copyFrom(this, side.select("Diff.ApplyLeftSide", "Diff.ApplyRightSide"))
  }

  override fun apply(side: ThreeSide, changes: List<TextMergeChange>) {
    viewer.replaceChanges(changes, this@ApplySelectedChangesAction.side, false)
  }

  override fun getText(side: ThreeSide): String? {
    return if (side != ThreeSide.BASE) DiffBundle.message("action.presentation.diff.accept.text") else getTemplatePresentation().text
  }

  override fun isVisible(side: ThreeSide): Boolean {
    if (side == ThreeSide.BASE) return true
    return side == this.side.select(ThreeSide.LEFT, ThreeSide.RIGHT)
  }

  override fun isEnabled(change: TextMergeChange): Boolean = !change.isResolved(side)
}

internal class ResolveSelectedChangesAction(
  viewer: MergeThreesideViewer,
  private val side: Side,
) : ApplySelectedChangesActionBase(viewer) {
  override fun apply(side: ThreeSide, changes: List<TextMergeChange>) {
    viewer.replaceChanges(changes, this@ResolveSelectedChangesAction.side, true)
  }

  override fun getText(side: ThreeSide): String {
    return DiffBundle.message("action.presentation.merge.resolve.using.side.text", side.index)
  }

  override fun isVisible(side: ThreeSide): Boolean {
    if (side == ThreeSide.BASE) return true
    return side == this.side.select(ThreeSide.LEFT, ThreeSide.RIGHT)
  }

  override fun isEnabled(change: TextMergeChange): Boolean {
    return !change.isResolved(side)
  }
}

internal class ResolveSelectedConflictsAction(viewer: MergeThreesideViewer) : ApplySelectedChangesActionBase(viewer) {
  init {
    copyFrom(this, "Diff.ResolveConflict")
  }

  override fun apply(side: ThreeSide, changes: List<TextMergeChange>) {
    viewer.resolveChangesAutomatically(changes, ThreeSide.BASE)
  }

  override fun getText(side: ThreeSide): String = DiffBundle.message("action.presentation.merge.resolve.automatically.text")

  override fun isVisible(side: ThreeSide): Boolean = side == ThreeSide.BASE

  override fun isEnabled(change: TextMergeChange): Boolean {
    return viewer.model.canResolveChangeAutomatically(change.index, ThreeSide.BASE)
  }
}
