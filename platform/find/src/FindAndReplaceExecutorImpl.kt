// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.find

import com.intellij.find.FindModel
import com.intellij.find.impl.FindAndReplaceExecutor
import com.intellij.find.impl.FindInProjectUtil
import com.intellij.find.impl.FindKey
import com.intellij.ide.vfs.rpcId
import com.intellij.ide.vfs.virtualFile
import com.intellij.openapi.application.readAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ex.ProgressIndicatorEx
import com.intellij.platform.project.projectId
import com.intellij.usages.FindUsagesProcessPresentation
import com.intellij.usages.UsageInfo2UsageAdapter
import com.intellij.usages.UsageInfoAdapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
open class FindAndReplaceExecutorImpl(val coroutineScope: CoroutineScope) : FindAndReplaceExecutor {

  override fun findUsages(
    project: Project,
    progressIndicator: ProgressIndicatorEx,
    presentation: FindUsagesProcessPresentation,
    findModel: FindModel,
    previousUsages: Set<UsageInfoAdapter>,
    onResult: (UsageInfoAdapter) -> Boolean,
    onFinish: () -> Unit?,
  ) {
    if (FindKey.isEnabled) {
      coroutineScope.launch {
        val filesToScanInitially = previousUsages.mapNotNull { (it as? UsageInfoModel)?.model?.fileId?.virtualFile() }.toSet()

        FindRemoteApi.getInstance().findByModel(findModel, project.projectId(), filesToScanInitially.map { it.rpcId() }).collect { findResult ->
          val usage = readAction {
            UsageInfoModel.createUsageInfoModel(project, findResult)
          }
          onResult(usage)

        }
        onFinish()
      }
    }
    else {
      val filesToScanInitially = previousUsages.mapNotNull { (it as? UsageInfo2UsageAdapter)?.file }.toSet()
      FindInProjectUtil.findUsages(findModel, project, presentation, filesToScanInitially) { info ->
        val usage = UsageInfo2UsageAdapter.CONVERTER.`fun`(info) as UsageInfoAdapter
        usage.presentation.icon // cache icon

        onResult(usage)
      }
      onFinish()
    }
  }

  override fun performFindAllOrReplaceAll(findModel: FindModel, project: Project) {
    coroutineScope.launch {
      FindRemoteApi.getInstance().performFindAllOrReplaceAll(findModel, project.projectId())
    }
  }
}