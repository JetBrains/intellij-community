// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.projectView.actions

import com.intellij.ide.projectView.impl.ProjectRootsUtil
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.workspaceModel.core.fileIndex.impl.OptionalExclusionUtil

internal class MarkAsContentRootAction : DumbAwareAction() {

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    val files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)
    val module = MarkRootActionBase.getModule(e, files)
    if (module == null || files == null) {
      e.presentation.isEnabledAndVisible = false
      return
    }
    val fileIndex = ProjectRootManager.getInstance(module.project).fileIndex
    e.presentation.isEnabledAndVisible = files.all {
      it.isDirectory && fileIndex.isExcluded(it) && ProjectRootsUtil.findExcludeFolder(module, it) == null
    }
  }

  override fun actionPerformed(e: AnActionEvent) {
    val files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY) ?: return
    val module = MarkRootActionBase.getModule(e, files) ?: return
    val contentRootsToAdd = ArrayList<VirtualFile>()
    for (file in files) {
      if (!OptionalExclusionUtil.cancelExclusion(module.project, file)) {
        contentRootsToAdd.add(file)
      }
    }
    if (contentRootsToAdd.isNotEmpty()) {
      val model = ModuleRootManager.getInstance(module).modifiableModel
      for (it in contentRootsToAdd) {
        model.addContentEntry(it)
      }
      MarkRootsManager.commitModel(module, model)
    }
  }
}
