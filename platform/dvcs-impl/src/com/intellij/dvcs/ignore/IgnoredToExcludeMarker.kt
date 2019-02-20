// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.dvcs.ignore

import com.intellij.ide.projectView.actions.MarkExcludeRootAction
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.VcsApplicationSettings
import com.intellij.openapi.vfs.VirtualFile
import java.util.*

private val excludeAction = object : MarkExcludeRootAction() {
  fun exclude(module: Module, dirs: Collection<VirtualFile>) = runInEdt { modifyRoots(module, dirs.toTypedArray()) }
}

class IgnoredToExcludeMarker(val project: Project) : VcsIgnoredHolderUpdateListener {

  override fun updateFinished(ignoredPaths: Collection<FilePath>) {
    if (!Registry.`is`("vcs.mark.ignored.as.excluded")) return
    if (project.isDisposed) return
    if (!VcsApplicationSettings.getInstance().MARK_IGNORED_AS_EXCLUDED) return

    convertIgnoredToExcluded(ignoredPaths)
  }

  private fun convertIgnoredToExcluded(ignoredPaths: Collection<FilePath>) {
    val ignoredDirsByModule =
      ignoredPaths
        .asSequence()
        .filter { it.isDirectory }
        .mapNotNull { it.virtualFile }
        .groupBy { ModuleUtil.findModuleForFile(it, project) }
        .filterKeys(Objects::nonNull)

    for ((module, ignoredDirs) in ignoredDirsByModule) {
      excludeAction.exclude(module!!, ignoredDirs)
    }
  }
}

