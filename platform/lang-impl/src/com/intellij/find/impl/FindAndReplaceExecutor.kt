// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.impl

import com.intellij.find.FindModel
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ex.ProgressIndicatorEx
import com.intellij.usageView.UsageInfo
import com.intellij.usages.FindUsagesProcessPresentation
import com.intellij.usages.UsageInfoAdapter
import org.jetbrains.annotations.ApiStatus
import javax.swing.table.TableCellRenderer

@ApiStatus.Internal
interface FindAndReplaceExecutor {

  companion object {
    @JvmStatic
    fun getInstance(): FindAndReplaceExecutor {
      return ApplicationManager.getApplication().getService(FindAndReplaceExecutor::class.java)
    }
  }

  fun createTableCellRenderer(): TableCellRenderer? {
    return null
  }

  fun findUsages(
    project: Project,
    progressIndicator: ProgressIndicatorEx,
    presentation: FindUsagesProcessPresentation,
    findModel: FindModel,
    previousUsages: Set<UsageInfoAdapter>,
    shouldThrottle: Boolean,
    disposableParent: Disposable,
    onDocumentUpdated: (usageInfos: List<UsageInfo>) -> Unit?,
    onResult: (UsageInfoAdapter) -> Boolean,
    onFinish: () -> Unit?,
  )

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

  fun validateModel(findModel: FindModel, onFinish: (Boolean) -> Any?)

  fun cancelActivities()

}