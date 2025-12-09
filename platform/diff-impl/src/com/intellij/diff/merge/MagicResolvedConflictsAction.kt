// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.merge

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ex.ActionUtil.copyFrom
import com.intellij.openapi.project.DumbAwareAction

class MagicResolvedConflictsAction(viewer: MergeThreesideViewer) : DumbAwareAction() {
    private val myViewer: MergeThreesideViewer

    init {
        copyFrom(this, "Diff.MagicResolveConflicts")
        this.myViewer = viewer
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.EDT
    }

    override fun update(e: AnActionEvent) {
        e.getPresentation().setEnabled(myViewer.hasResolvableConflictedChanges() && !myViewer.isExternalOperationInProgress())
    }

    override fun actionPerformed(e: AnActionEvent) {
        myViewer.applyResolvableConflictedChanges()
    }
}
