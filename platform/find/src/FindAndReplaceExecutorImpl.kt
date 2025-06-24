// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.find

import com.intellij.find.FindModel
import com.intellij.find.FindSettings
import com.intellij.find.findInProject.FindInProjectManager
import com.intellij.find.impl.FindAndReplaceExecutor
import com.intellij.find.impl.FindInProjectUtil
import com.intellij.find.impl.FindKey
import com.intellij.find.replaceInProject.ReplaceInProjectManager
import com.intellij.ide.rpc.ThrottledAccumulatedItems
import com.intellij.ide.rpc.ThrottledOneItem
import com.intellij.ide.rpc.throttledWithAccumulation
import com.intellij.ide.vfs.rpcId
import com.intellij.ide.vfs.virtualFile
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ex.ProgressIndicatorEx
import com.intellij.platform.project.projectId
import com.intellij.platform.util.coroutines.childScope
import com.intellij.usages.FindUsagesProcessPresentation
import com.intellij.usages.UsageInfo2UsageAdapter
import com.intellij.usages.UsageInfoAdapter
import fleet.rpc.client.RpcTimeoutException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus.Internal
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@Internal
open class FindAndReplaceExecutorImpl(val coroutineScope: CoroutineScope) : FindAndReplaceExecutor {
  private var validationJob: Job? = null
  private var findUsagesJob: Job? = null

  @OptIn(ExperimentalAtomicApi::class)
  override fun findUsages(
    project: Project,
    progressIndicator: ProgressIndicatorEx,
    presentation: FindUsagesProcessPresentation,
    findModel: FindModel,
    previousUsages: Set<UsageInfoAdapter>,
    shouldThrottle: Boolean,
    onResult: (UsageInfoAdapter) -> Boolean,
    onFinish: () -> Unit?,
  ) {
    if (FindKey.isEnabled) {
      findUsagesJob?.cancel("new find request is started")
      findUsagesJob = coroutineScope.launch {
        val filesToScanInitially = previousUsages.mapNotNull { (it as? UsageInfoModel)?.model?.fileId?.virtualFile() }.toSet()

        FindRemoteApi.getInstance().findByModel(findModel, project.projectId(), filesToScanInitially.map { it.rpcId() })
          .let {
            if (shouldThrottle) it.throttledWithAccumulation()
            else it.map { event -> ThrottledOneItem(event) }
          }.collect { throttledItems ->
            when (throttledItems) {
              is ThrottledOneItem -> {
                val usage = UsageInfoModel.createUsageInfoModel(project, throttledItems.item, this.childScope("UsageInfoModel.init"))
                onResult(usage)
              }
              is ThrottledAccumulatedItems -> {
                throttledItems.items.forEach {
                  val usage = UsageInfoModel.createUsageInfoModel(project, it, this.childScope("UsageInfoModel.init"))
                  onResult(usage)
                }
              }
            }

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