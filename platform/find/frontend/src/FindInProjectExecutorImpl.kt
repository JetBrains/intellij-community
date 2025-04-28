// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.find.frontend

import com.intellij.find.FindModel
import com.intellij.find.impl.FindInProjectExecutor
import com.intellij.find.impl.FindInProjectUtil
import com.intellij.find.impl.FindKey
import com.intellij.ide.vfs.rpcId
import com.intellij.ide.vfs.virtualFile
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ex.ProgressIndicatorEx
import com.intellij.platform.find.FindInProjectModel
import com.intellij.platform.find.FindRemoteApi
import com.intellij.platform.find.SearchScopeProvider
import com.intellij.platform.find.UsageInfoModel
import com.intellij.platform.project.projectId
import com.intellij.usages.FindUsagesProcessPresentation
import com.intellij.usages.UsageInfo2UsageAdapter
import com.intellij.usages.UsageInfoAdapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
open class FindInProjectExecutorImpl(val coroutineScope: CoroutineScope) : FindInProjectExecutor() {

  override fun findUsages(
    project: Project,
    progressIndicator: ProgressIndicatorEx,
    presentation: FindUsagesProcessPresentation,
    findModel: FindModel,
    previousUsages: Set<UsageInfoAdapter>,
    onResult: (UsageInfoAdapter, Int?, Int?) -> Boolean,
    onFinish: () -> Unit?
  ) {
    if (findModel.stringToFind.isBlank()) {
      return
    }


    if (FindKey.isEnabled) {
      coroutineScope.launch {
        val filesToScanInitially = previousUsages.mapNotNull { (it as? UsageInfoModel)?.mergedModel?.fileId?.virtualFile() }.toSet()
        FindRemoteApi.getInstance().findByModel(getModel(project, findModel, filesToScanInitially)).collect { findResult ->
          val usage = UsageInfoModel(project, findResult, coroutineScope)
          onResult(usage, findResult.usagesCount, findResult.fileCount)
        }
        onFinish()
      }
    }
    else {
      super.findUsages(project, progressIndicator, presentation, findModel, previousUsages, onResult, onFinish)
    }
  }
}

private fun getModel(project: Project, findModel: FindModel, filesToScanInitially: Set<VirtualFile>): FindInProjectModel {
  return FindInProjectModel(projectId = project.projectId(),
                            stringToFind = findModel.stringToFind,
                            isWholeWordsOnly = findModel.isWholeWordsOnly,
                            isRegularExpressions = findModel.isRegularExpressions,
                            isCaseSensitive = findModel.isCaseSensitive,
                            isMultiline = findModel.isMultiline,
                            isPreserveCase = findModel.isPreserveCase,
                            isProjectScope = findModel.isProjectScope,
                            isCustomScope = findModel.isCustomScope,
                            isMultipleFiles = findModel.isMultipleFiles,
                            isReplaceState = findModel.isReplaceState,
                            isPromptOnReplace = findModel.isPromptOnReplace,
                            fileFilter = findModel.fileFilter,
                            moduleName = findModel.moduleName,
                            directoryName = findModel.directoryName,
                            isWithSubdirectories = findModel.isWithSubdirectories,
                            searchContext = findModel.searchContext.name,
                            filesToScanInitially = filesToScanInitially.map { it.rpcId() },
                            scopeId = findModel.customScope?.let { SearchScopeProvider.getScopeId(it.displayName) }, //TODO rework
                            )
}