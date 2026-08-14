// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.impl

import com.intellij.find.FindModel
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface FindAndReplaceService {
  companion object {
    @JvmStatic
    fun getInstance(project: Project): FindAndReplaceService {
      return project.getService(FindAndReplaceService::class.java)
    }
  }

  fun createExecutor(parentDisposable: Disposable, scopeUI: FindPopupScopeUI): FindAndReplaceExecutor

  /**
   * Initiates a "Find all"/"Replace all" operation on the backend and displays results in the Find tool window.
   * NOTE: Currently, the operation is performed on the backend only,
   * should be reworked when Find tool window is split for remote development.
   *
   * This function handles searching for text based on the provided search model
   *
   * @param findModel the model containing search parameters and criteria
   * @param project the project where the search is performed
   */
  fun performFindAllOrReplaceAll(findModel: FindModel, project: Project)
}
