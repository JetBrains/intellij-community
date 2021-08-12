// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing

import com.intellij.ide.actions.SynchronizeCurrentFileAction
import com.intellij.ide.actions.cache.RecoveryAction
import com.intellij.lang.LangBundle
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import org.jetbrains.annotations.Nls

internal class RefreshIndexableFilesAction : RecoveryAction {
  override val performanceRate: Int
    get() = 9999
  override val presentableName: @Nls(capitalization = Nls.Capitalization.Title) String
    get() = LangBundle.message("refresh.indexable.files.recovery.action.name")
  override val actionKey: String
    get() = "refresh"

  override fun perform(project: Project?) {
    val fileBasedIndex = FileBasedIndex.getInstance() as FileBasedIndexImpl
    val rootUrls = fileBasedIndex.getOrderedIndexableFilesProviders(project!!).flatMap { it.rootUrls }
    val files = arrayListOf<VirtualFile>()
    for (rootUrl in rootUrls) {
      val file = VirtualFileManager.getInstance().refreshAndFindFileByUrl(rootUrl)
      if (file != null) {
        files.add(file)
      }
    }
    SynchronizeCurrentFileAction.synchronizeFiles(files, project)
  }

  override fun canBeApplied(project: Project?): Boolean = project != null
}