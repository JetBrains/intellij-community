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
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.ex.ActionUtil.copyFrom
import com.intellij.openapi.diff.DiffBundle
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.Condition
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.concurrency.annotations.RequiresWriteLock
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.Unmodifiable
import java.util.*

internal class MergeThreesideViewerActions {
    private abstract class ApplySelectedChangesActionBase protected constructor(protected var myViewer: MergeThreesideViewer) : AnAction(),
        DumbAware {
        override fun getActionUpdateThread(): ActionUpdateThread {
            return ActionUpdateThread.EDT
        }

        override fun update(e: AnActionEvent) {
            if (DiffUtil.isFromShortcut(e)) {
                // consume shortcut even if there are nothing to do - avoid calling some other action
                e.getPresentation().setEnabledAndVisible(true)
                return
            }

            val presentation = e.getPresentation()
            val editor = e.getData<Editor?>(CommonDataKeys.EDITOR)

            val side = myViewer.getEditorSide(editor)
            if (side == null) {
                presentation.setEnabledAndVisible(false)
                return
            }

            if (!isVisible(side)) {
                presentation.setEnabledAndVisible(false)
                return
            }

            presentation.setText(getText(side))

            presentation.setEnabledAndVisible(isSomeChangeSelected(side) && !myViewer.isExternalOperationInProgress())
        }

        override fun actionPerformed(e: AnActionEvent) {
            val editor = e.getData<Editor?>(CommonDataKeys.EDITOR)
            val side = myViewer.getEditorSide(editor)
            if (editor == null || side == null) return

            val selectedChanges = getSelectedChanges(side)
            if (selectedChanges.isEmpty()) return

            val title = DiffBundle.message("message.do.in.merge.command", e.getPresentation().getText())
            myViewer.executeMergeCommand(title, selectedChanges.size > 1, selectedChanges, Runnable { apply(side, selectedChanges) })
        }

        @RequiresWriteLock
        protected abstract fun apply(side: ThreeSide, changes: MutableList<out TextMergeChange?>)

        fun isSomeChangeSelected(side: ThreeSide): Boolean {
            val editor = myViewer.getEditor(side)
            return DiffUtil.isSomeRangeSelected(
                editor,
                Condition { lines: BitSet? ->
                    ContainerUtil.exists<TextMergeChange?>(
                        myViewer.getAllChanges(),
                        Condition { change: TextMergeChange? -> isChangeSelected(change!!, lines!!, side) })
                })
        }

        @RequiresEdt
        protected open fun getSelectedChanges(side: ThreeSide): @Unmodifiable MutableList<TextMergeChange?> {
            val editor = myViewer.getEditor(side)
            val lines = DiffUtil.getSelectedLines(editor)
            return ContainerUtil.filter<TextMergeChange?>(
                myViewer.getChanges(),
                Condition { change: TextMergeChange? -> isChangeSelected(change!!, lines, side) })
        }

        protected fun isChangeSelected(change: TextMergeChange, lines: BitSet, side: ThreeSide): Boolean {
            if (!isEnabled(change)) return false
            val line1 = change.getStartLine(side)
            val line2 = change.getEndLine(side)
            return DiffUtil.isSelectedByLine(lines, line1, line2)
        }

        @Nls
        protected abstract fun getText(side: ThreeSide): @Nls String?

        protected abstract fun isVisible(side: ThreeSide): Boolean

        protected abstract fun isEnabled(change: TextMergeChange): Boolean
    }

    class IgnoreSelectedChangesSideAction internal constructor(viewer: MergeThreesideViewer, private val mySide: Side) :
        ApplySelectedChangesActionBase(viewer) {
        init {
            ActionUtil.copyFrom(this, mySide.select<String?>("Diff.IgnoreLeftSide", "Diff.IgnoreRightSide")!!)
        }

        override fun apply(side: ThreeSide, changes: MutableList<out TextMergeChange>) {
            for (change in changes) {
                myViewer.ignoreChange(change, mySide, false)
            }
        }

        override fun getText(side: ThreeSide): String {
            return DiffBundle.message("action.presentation.merge.ignore.text")
        }

        override fun isVisible(side: ThreeSide): Boolean {
            return side == mySide.select<ThreeSide>(ThreeSide.LEFT, ThreeSide.RIGHT)
        }

        override fun isEnabled(change: TextMergeChange): Boolean {
            return !change.isResolved(mySide)
        }
    }

    class IgnoreSelectedChangesAction internal constructor(viewer: MergeThreesideViewer) : ApplySelectedChangesActionBase(viewer) {
        init {
            getTemplatePresentation().setIcon(AllIcons.Diff.Remove)
        }

        override fun getText(side: ThreeSide): String {
            return DiffBundle.message("action.presentation.merge.ignore.text")
        }

        override fun isVisible(side: ThreeSide): Boolean {
            return side == ThreeSide.BASE
        }

        override fun isEnabled(change: TextMergeChange): Boolean {
            return !change.isResolved
        }

        override fun apply(side: ThreeSide, changes: MutableList<out TextMergeChange>) {
            for (change in changes) {
                myViewer.markChangeResolved(change)
            }
        }
    }

    class ResetResolvedChangeAction internal constructor(viewer: MergeThreesideViewer) : ApplySelectedChangesActionBase(viewer) {
        init {
            getTemplatePresentation().setIcon(AllIcons.Diff.Revert)
        }

        override fun apply(side: ThreeSide, changes: MutableList<out TextMergeChange>) {
            for (change in changes) {
                myViewer.resetResolvedChange(change)
            }
        }

        override fun getSelectedChanges(side: ThreeSide): @Unmodifiable MutableList<TextMergeChange?> {
            val editor = myViewer.getEditor(side)
            val lines = DiffUtil.getSelectedLines(editor)
            return ContainerUtil.filter<TextMergeChange?>(
                myViewer.getAllChanges(),
                Condition { change: TextMergeChange? -> isChangeSelected(change!!, lines, side) })
        }

        @Nls
        override fun getText(side: ThreeSide): @Nls String {
            return DiffBundle.message("action.presentation.diff.revert.text")
        }

        override fun isVisible(side: ThreeSide): Boolean {
            return true
        }

        override fun isEnabled(change: TextMergeChange): Boolean {
            return change.isResolvedWithAI
        }
    }

    class ApplySelectedChangesAction internal constructor(viewer: MergeThreesideViewer, private val mySide: Side) :
        ApplySelectedChangesActionBase(viewer) {
        init {
            ActionUtil.copyFrom(this, mySide.select<String?>("Diff.ApplyLeftSide", "Diff.ApplyRightSide")!!)
        }

        override fun apply(side: ThreeSide, changes: MutableList<out TextMergeChange?>) {
            myViewer.replaceChanges(changes, mySide, false)
        }

        override fun getText(side: ThreeSide): String? {
            return if (side != ThreeSide.BASE) DiffBundle.message("action.presentation.diff.accept.text") else getTemplatePresentation().getText()
        }

        override fun isVisible(side: ThreeSide): Boolean {
            if (side == ThreeSide.BASE) return true
            return side == mySide.select<ThreeSide>(ThreeSide.LEFT, ThreeSide.RIGHT)
        }

        override fun isEnabled(change: TextMergeChange): Boolean {
            return !change.isResolved(mySide)
        }
    }

    class ResolveSelectedChangesAction internal constructor(viewer: MergeThreesideViewer, private val mySide: Side) :
        ApplySelectedChangesActionBase(viewer) {
        override fun apply(side: ThreeSide, changes: MutableList<out TextMergeChange?>) {
            myViewer.replaceChanges(changes, mySide, true)
        }

        override fun getText(side: ThreeSide): String {
            return DiffBundle.message("action.presentation.merge.resolve.using.side.text", mySide.index)
        }

        override fun isVisible(side: ThreeSide): Boolean {
            if (side == ThreeSide.BASE) return true
            return side == mySide.select<ThreeSide>(ThreeSide.LEFT, ThreeSide.RIGHT)
        }

        override fun isEnabled(change: TextMergeChange): Boolean {
            return !change.isResolved(mySide)
        }
    }

    class ResolveSelectedConflictsAction internal constructor(viewer: MergeThreesideViewer) : ApplySelectedChangesActionBase(viewer) {
        init {
            copyFrom(this, "Diff.ResolveConflict")
        }

        override fun apply(side: ThreeSide, changes: MutableList<out TextMergeChange?>) {
            myViewer.resolveChangesAutomatically(changes, ThreeSide.BASE)
        }

        override fun getText(side: ThreeSide): String {
            return DiffBundle.message("action.presentation.merge.resolve.automatically.text")
        }

        override fun isVisible(side: ThreeSide): Boolean {
            return side == ThreeSide.BASE
        }

        override fun isEnabled(change: TextMergeChange): Boolean {
            return myViewer.canResolveChangeAutomatically(change, ThreeSide.BASE)
        }
    }
}
