// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.find

import com.intellij.find.FindModel
import com.intellij.find.FindSettings
import com.intellij.find.actions.ShowUsagesAction
import com.intellij.find.findInProject.FindInProjectManager
import com.intellij.find.impl.FindAndReplaceExecutor
import com.intellij.find.impl.FindInProjectUtil
import com.intellij.find.impl.FindKey
import com.intellij.find.replaceInProject.ReplaceInProjectManager
import com.intellij.ide.rpc.ThrottledOneItem
import com.intellij.ide.rpc.performRpcWithRetries
import com.intellij.ide.rpc.throttledWithAccumulation
import com.intellij.ide.vfs.rpcId
import com.intellij.ide.vfs.virtualFile
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.CheckedDisposable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ex.ProgressIndicatorEx
import com.intellij.platform.project.projectId
import com.intellij.platform.util.coroutines.childScope
import com.intellij.usageView.UsageInfo
import com.intellij.usages.FindUsagesProcessPresentation
import com.intellij.usages.UsageInfo2UsageAdapter
import com.intellij.usages.UsageInfoAdapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus.Internal
import kotlin.concurrent.atomics.ExperimentalAtomicApi

private val LOG = logger<FindAndReplaceExecutorImpl>()
@Internal
open class FindAndReplaceExecutorImpl(val coroutineScope: CoroutineScope) : FindAndReplaceExecutor {
  private var validationJob: Job? = null
  private var findUsagesJob: Job? = null
  private var currentSearchDisposable: CheckedDisposable? = null

  @OptIn(ExperimentalAtomicApi::class)
  override fun findUsages(
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
  ) {
    if (FindKey.isEnabled) {
      findUsagesJob?.cancel("new find request is started")
      findUsagesJob = coroutineScope.launch {
        val filesToScanInitially = previousUsages.mapNotNull { (it as? UsageInfoModel)?.model?.fileId?.virtualFile() }.toSet()
        currentSearchDisposable?.let { Disposer.dispose(it) }
        currentSearchDisposable = Disposer.newCheckedDisposable(disposableParent, "Find in Project Search")

        FindRemoteApi.getInstance().findByModel(
          findModel = findModel,
          projectId = project.projectId(),
          filesToScanInitially = filesToScanInitially.map { it.rpcId() },
          maxUsagesCount = ShowUsagesAction.getUsagesPageSize()
        ).let {
            if (shouldThrottle) it.throttledWithAccumulation()
            else it.map { event -> ThrottledOneItem(event) }
          }.collect { throttledItems ->
            throttledItems.items.forEach { item ->
              val usage = UsageInfoModel.createUsageInfoModel(project, item, this.childScope("UsageInfoModel.init"), onDocumentUpdated)
              currentSearchDisposable?.let { parent ->
                if (parent.isDisposed) return@collect
                Disposer.register(parent, usage)
              }
              onResult(usage)
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
      LOG.performRpcWithRetries(
        rpcCall = { FindRemoteApi.getInstance().checkDirectoryExists(findModel).let { onFinish(it) } },
        onRpcTimeout = { onFinish(false) })
    }
  }

  override fun cancelActivities() {
    val message = "cancel all activities for find and replace executor"
    validationJob?.cancel(message)
    findUsagesJob?.cancel(message)
  }
}