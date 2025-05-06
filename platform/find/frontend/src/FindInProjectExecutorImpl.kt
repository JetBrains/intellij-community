// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.find.frontend

import com.intellij.find.FindModel
import com.intellij.find.impl.FindInProjectExecutor
import com.intellij.find.impl.FindKey
import com.intellij.ide.vfs.rpcId
import com.intellij.ide.vfs.virtualFile
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ex.ProgressIndicatorEx
import com.intellij.platform.find.FindRemoteApi
import com.intellij.platform.find.UsageInfoModel
import com.intellij.platform.project.projectId
import com.intellij.usages.FindUsagesProcessPresentation
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
    onResult: (UsageInfoAdapter) -> Boolean,
    onFinish: () -> Unit?,
  ) {
    if (findModel.stringToFind.isBlank()) {
      onFinish()
      return
    }

    if (FindKey.isEnabled) {
      coroutineScope.launch {
        val filesToScanInitially = previousUsages.mapNotNull { (it as? UsageInfoModel)?.mergedModel?.fileId?.virtualFile() }.toSet()

        //TODO provide custom scope and search context (it's not serializable in FindModel)
        FindRemoteApi.getInstance().findByModel(findModel, project.projectId(), filesToScanInitially.map { it.rpcId() }).collect { findResult ->
          val usage = UsageInfoModel(project, findResult, coroutineScope)
          onResult(usage)
        }
        onFinish()
      }
    }
    else {
      super.findUsages(project, progressIndicator, presentation, findModel, previousUsages, onResult, onFinish)
    }
  }
}