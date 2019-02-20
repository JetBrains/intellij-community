// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.dvcs.ignore

import com.intellij.ide.projectView.actions.MarkExcludeRootAction
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.VcsApplicationSettings
import com.intellij.openapi.vcs.changes.shelf.ShelveChangesManager
import com.intellij.openapi.vfs.VirtualFile
import java.util.*

private val excludeAction = object : MarkExcludeRootAction() {
  fun exclude(module: Module, dirs: Collection<VirtualFile>) = runInEdt { modifyRoots(module, dirs.toTypedArray()) }
}

class IgnoredToExcludedSynchronizer(private val project: Project) : VcsIgnoredHolderUpdateListener {

  override fun updateFinished(ignoredPaths: Collection<FilePath>) {
    ProgressManager.checkCanceled()
    if (!VcsApplicationSettings.getInstance().MARK_IGNORED_AS_EXCLUDED) return

    markIgnoredAsExcluded(ignoredPaths)
  }

  private fun markIgnoredAsExcluded(ignoredPaths: Collection<FilePath>) {
    val ignoredDirsByModule =
      ignoredPaths
        .asSequence()
        .filter(FilePath::isDirectory)
        .filterNot(::isShelfDirectoryOrUnder) //shelf directory usually contains in project and excluding it prevents local history to work
        .mapNotNull(FilePath::getVirtualFile)
        .groupBy { ModuleUtil.findModuleForFile(it, project) }
        .filterKeys(Objects::nonNull) //if the directory already excluded then ModuleUtil.findModuleForFile return null and this will filter out such directories from processing.

    for ((module, ignoredDirs) in ignoredDirsByModule) {
      excludeAction.exclude(module!!, ignoredDirs)
    }
  }

  private fun isShelfDirectoryOrUnder(filePath: FilePath) =
    FileUtil.isAncestor(ShelveChangesManager.getShelfPath(project), filePath.path, false);
}

