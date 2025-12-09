// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.merge

import com.intellij.diff.util.ThreeSide
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.project.DumbAwareAction
import org.jetbrains.annotations.Nls

internal class ApplyNonConflictsAction(viewer: MergeThreesideViewer, side: ThreeSide, @Nls text: @Nls String) : DumbAwareAction() {
    private val mySide: ThreeSide
    private val myViewer: MergeThreesideViewer

    init {
        val id = side.select<String?>("Diff.ApplyNonConflicts.Left", "Diff.ApplyNonConflicts", "Diff.ApplyNonConflicts.Right")
        ActionUtil.copyFrom(this, id!!)
        mySide = side
        getTemplatePresentation().setText(text)
        this.myViewer = viewer
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.EDT
    }

    override fun update(e: AnActionEvent) {
        e.getPresentation().setEnabled(myViewer.hasNonConflictedChanges(mySide) && !myViewer.isExternalOperationInProgress())
    }

    override fun actionPerformed(e: AnActionEvent) {
        myViewer.applyNonConflictedChanges(mySide)
    }

    override fun displayTextInToolbar(): Boolean {
        return true
    }

    override fun useSmallerFontForTextInToolbar(): Boolean {
        return true
    }
}
