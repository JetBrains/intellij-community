// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.dvcs.actions

import com.intellij.dvcs.DvcsUtil
import com.intellij.dvcs.repo.Repository
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.DumbAwareAction
import java.awt.datatransfer.StringSelection

internal class CopyCurrentBranchNameAction : DumbAwareAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val project = e.project
        e.presentation.isEnabledAndVisible = project != null && DvcsUtil.guessRepositoryForOperation(project, e.dataContext) != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val repository = DvcsUtil.guessRepositoryForOperation(project, e.dataContext) ?: return

        val branchName = when (repository.getState()) {
            Repository.State.DETACHED -> repository.getCurrentRevision()
            else -> repository.getCurrentBranchName()
        } ?: return

        CopyPasteManager.getInstance().setContents(StringSelection(branchName))
    }
}
