// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.formatting.service

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus

/**
 * Handles tasks that need to be run after file changes are made from quick fixes.
 *
 * In IDEA, such tasks can be run immediately.
 * In Fleet, the backend is not allowed to modify files directly and basically doesn't know when the files will actually be ready.
 * This is why quick fixes can register post-actions to be run by Fleet whenever the quick fixes file changes were actually committed.
 */
@ApiStatus.Internal
interface PostQuickFixTaskService {

  /**
   * Executes the given [block] after changes to [filesToSave] (made during the quickfix) are actually saved in OS
   * and changed documents' contents are available for third party processes (e.g. build systems)
   * This can happen immediately if the documents are already saved (e.g. in IDEA), but will be delayed if not (e.g. in Fleet).
   * If block doesn't invoke third party processes, it's ok to leave filesToSave empty
   */
  fun runOrRegisterPostQuickFixTask(filesToSave: List<VirtualFile>, block: () -> Unit)

  companion object {
    fun getInstance(project: Project): PostQuickFixTaskService = project.service<PostQuickFixTaskService>()
  }
}
