// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.impl

import com.intellij.find.FindModel
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ex.ProgressIndicatorEx
import com.intellij.usages.FindUsagesProcessPresentation
import com.intellij.usages.UsageInfoAdapter
import org.jetbrains.annotations.ApiStatus
import java.util.function.Consumer
import javax.swing.table.TableCellRenderer

@ApiStatus.Internal
interface FindAndReplaceExecutor {

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
    onUpdateModelCallback: Consumer<UsageInfoAdapter>,
    onResult: (UsageInfoAdapter) -> Boolean,
    onFinish: () -> Unit?,
    maxUsages: Int,
    isLoadMore: Boolean = false,
  )

  fun validateModel(findModel: FindModel, onFinish: (Boolean) -> Any?)

  fun cancelActivities()

}
