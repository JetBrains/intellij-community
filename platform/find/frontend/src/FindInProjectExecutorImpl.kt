// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.find.frontend

import com.intellij.find.FindModel
import com.intellij.find.impl.FindInProjectExecutor
import com.intellij.find.impl.FindInProjectUtil
import com.intellij.find.impl.FindKey
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ex.ProgressIndicatorEx
import com.intellij.platform.find.*
import com.intellij.platform.project.projectId
import com.intellij.ui.SimpleTextAttributes
import com.intellij.usages.FindUsagesProcessPresentation
import com.intellij.usages.UsageInfo2UsageAdapter
import com.intellij.usages.UsageInfoAdapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import java.awt.Color
import javax.swing.table.TableCellRenderer

@ApiStatus.Internal
open class FindInProjectExecutorImpl(val coroutineScope: CoroutineScope) : FindInProjectExecutor() {

  override fun findUsages(
    project: Project,
    progressIndicator: ProgressIndicatorEx,
    presentation: FindUsagesProcessPresentation,
    findModel: FindModel,
    filesToScanInitially: Set<VirtualFile>,
    onResult: (UsageInfoAdapter, Int?, Int?) -> Boolean,
    onFinish: () -> Unit?
  ) {
    if (findModel.stringToFind.isBlank()) {
      return
    }


    if (FindKey.isEnabled) {
      coroutineScope.launch {
        FindRemoteApi.getInstance().findByModel(getModel(project, findModel)).collect { findResult ->
          val usage = UsageInfoModel(project, findResult, coroutineScope)
          onResult(usage, findResult.usagesCount, findResult.fileCount)
        }
        onFinish()
      }
    }
    else {
      FindInProjectUtil.findUsages(findModel, project, presentation, filesToScanInitially) { info ->
        val usage = UsageInfo2UsageAdapter.CONVERTER.`fun`(info) as UsageInfoAdapter
        usage.presentation.icon // cache icon

        onResult.invokeWithDefault(usage)
      }
      onFinish()
    }
  }
}

private fun getModel(project: Project, findModel: FindModel): FindInProjectModel {
  return FindInProjectModel(projectId = project.projectId(),
                            stringToFind = findModel.stringToFind,
                            isWholeWordsOnly = findModel.isWholeWordsOnly,
                            isRegularExpressions = findModel.isRegularExpressions,
                            isCaseSensitive = findModel.isCaseSensitive,
                            isMultiline = findModel.isMultiline,
                            isPreserveCase = findModel.isPreserveCase,
                            isProjectScope = findModel.isProjectScope,
                            isMultipleFiles = findModel.isMultipleFiles,
                            isReplaceState = findModel.isReplaceState,
                            isPromptOnReplace = findModel.isPromptOnReplace,
                            fileFilter = findModel.fileFilter,
                            moduleName = findModel.moduleName,
                            searchContext = findModel.searchContext.name,
                            scopeId = findModel.customScope?.let { SearchScopeProvider.getScopeId(it.displayName) }, //TODO rework
                            )
}