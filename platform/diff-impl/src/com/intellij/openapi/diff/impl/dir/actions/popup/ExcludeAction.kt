// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.diff.impl.dir.actions.popup

import com.intellij.ide.diff.DiffElement
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diff.impl.dir.DirDiffPanel
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile

internal class ExcludeAction : DumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val dirDiffModel = e.getData(DirDiffPanel.DIR_DIFF_MODEL) ?: return

    val sourceContentRoot = dirDiffModel.sourceDir.value as? VirtualFile?
    val targetContentRoot = dirDiffModel.targetDir.value as? VirtualFile?
    val filter = e.getData(DirDiffPanel.DIR_DIFF_FILTER) ?: return
    var filterQuery = filter.filter

    val selectedSeparator = dirDiffModel.selectedSeparator
    if (selectedSeparator != null) {
      filterQuery = excludeFileFromScope(selectedSeparator.name, filterQuery, true)
    }
    else {
      dirDiffModel.selectedElements.forEach {
        if (it.isSource && sourceContentRoot != null) {
          filterQuery = excludeFileFromScope(it.source, sourceContentRoot, filterQuery)
        }
        else if (targetContentRoot != null) {
          filterQuery = excludeFileFromScope(it.target, targetContentRoot, filterQuery)
        }
      }
    }
    filter.filter = filterQuery ?: ""
    filter.userTriggeredFilter()
  }

  private fun excludeFileFromScope(
    diffElement: DiffElement<Any>,
    sourceContentRoot: VirtualFile,
    filterQuery: String
  ): String {
    val file = diffElement.value as? VirtualFile ?: return filterQuery
    val relativePath = VfsUtil.getRelativePath(file, sourceContentRoot) ?: return filterQuery
    return excludeFileFromScope(relativePath, filterQuery, file.isDirectory)
  }

  private fun excludeFileFromScope(relativePath: String, filterQuery: String, isDir: Boolean): String {
    val pattern = "!$relativePath${if (isDir) "*" else ""}"
    if (filterQuery.isBlank()) return pattern
    return "$filterQuery&$pattern"
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
}
