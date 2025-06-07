// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.find

import com.intellij.find.FindModel
import com.intellij.find.FindSettings
import com.intellij.find.findInProject.FindInProjectManager
import com.intellij.find.impl.FindAndReplaceExecutor
import com.intellij.find.impl.FindInProjectUtil
import com.intellij.find.impl.FindKey
import com.intellij.find.replaceInProject.ReplaceInProjectManager
import com.intellij.ide.vfs.rpcId
import com.intellij.ide.vfs.virtualFile
import com.intellij.openapi.application.readAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ex.ProgressIndicatorEx
import com.intellij.platform.project.projectId
import com.intellij.usages.FindUsagesProcessPresentation
import com.intellij.usages.UsageInfo2UsageAdapter
import com.intellij.usages.UsageInfoAdapter
import fleet.rpc.client.RpcTimeoutException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
open class FindAndReplaceExecutorImpl(val coroutineScope: CoroutineScope) : FindAndReplaceExecutor {
  private var validationJob: Job? = null
  private var findUsagesJob: Job? = null

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
      if (findUsagesJob?.isActive == true) {
        findUsagesJob?.cancel("new find request is started")
      }
      findUsagesJob = coroutineScope.launch {
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
    }
  }

  override fun performFindAllOrReplaceAll(findModel: FindModel, project: Project) {
    if (FindKey.isEnabled) {
      coroutineScope.launch {
        FindRemoteApi.getInstance().performFindAllOrReplaceAll(findModel, FindSettings.getInstance().isShowResultsInSeparateView, project.projectId())
      }
    } else {
      if (findModel.isReplaceState) {
        ReplaceInProjectManager.getInstance(project).replaceInPath(findModel)
      } else {
        FindInProjectManager.getInstance(project).findInPath(findModel)
      }
    }
  }

  override fun validateModel(findModel: FindModel, onFinish: (isDirectoryExists: Boolean) -> Any?) {
    if (validationJob?.isActive == true) {
      validationJob?.cancel("new validation request is started")
    }
    validationJob = coroutineScope.launch {
      try {
        FindRemoteApi.getInstance().checkDirectoryExists(findModel).let { onFinish(it) }
      } catch (_: RpcTimeoutException) {
        onFinish(false)
      }
    }
  }

  override fun cancelActivities() {
    val message = "cancel all activities for find and replace executor"
    validationJob?.cancel(message)
    findUsagesJob?.cancel(message)
  }
}