// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.openapi.util.text.StringUtil
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileIndex
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileSet
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileSetWithCustomData

@Suppress("HardCodedStringLiteral")
internal class ShowWorkspaceFileStateAction : DumbAwareAction() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val dataContext = e.dataContext
    val virtualFile = CommonDataKeys.VIRTUAL_FILE.getData(dataContext) ?: return

    val fileSets = WorkspaceFileIndex
      .getInstance(project)
      .findFileSets(virtualFile, true, true, true, true, true, true)

    val baseListPopupStep = object : BaseListPopupStep<WorkspaceFileSet>("Workspace File State", fileSets) {
      override fun isSpeedSearchEnabled() = true

      override fun getTextFor(fileSet: WorkspaceFileSet?): String =
        if (fileSet == null) ""
        else {
          fileSet as WorkspaceFileSetWithCustomData<*>
          "${StringUtil.shortenPathWithEllipsis(fileSet.root.path, 100)}: WorkspaceFileKind.${fileSet.kind}; " +
          "${if (fileSet.recursive) "recursive" else "non-recursive"}; (Data: ${fileSet.data})"
        }
    }

    JBPopupFactory.getInstance()
      .createListPopup(baseListPopupStep)
      .showInBestPositionFor(e.dataContext)
  }
}
