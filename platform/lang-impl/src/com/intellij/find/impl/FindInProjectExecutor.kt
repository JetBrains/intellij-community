// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.impl

import com.intellij.find.FindModel
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ex.ProgressIndicatorEx
import com.intellij.usages.FindUsagesProcessPresentation
import com.intellij.usages.UsageInfoAdapter
import org.jetbrains.annotations.ApiStatus
import javax.swing.table.TableCellRenderer

@ApiStatus.Internal
abstract class FindInProjectExecutor{

  companion object {
    fun getInstance(): FindInProjectExecutor {
      return ApplicationManager.getApplication().getService(FindInProjectExecutor::class.java)
    }
  }

  open fun createTableCellRenderer(): TableCellRenderer? {
    return null
  }

  abstract fun findUsages(
    project: Project,
    progressIndicator: ProgressIndicatorEx,
    presentation: FindUsagesProcessPresentation,
    findModel: FindModel,
    previousUsages: Set<UsageInfoAdapter>,
    onResult: (UsageInfoAdapter) -> Boolean,
    onFinish: () -> Unit?,
  )
}