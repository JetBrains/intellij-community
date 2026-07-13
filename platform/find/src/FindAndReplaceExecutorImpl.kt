// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.find

import com.intellij.find.FindModel
import com.intellij.find.FindSettings
import com.intellij.find.findInProject.FindInProjectManager
import com.intellij.find.impl.FindAndReplaceExecutor
import com.intellij.find.impl.FindInProjectUtil
import com.intellij.find.impl.FindKey
import com.intellij.find.replaceInProject.ReplaceInProjectManager
import com.intellij.ide.rpc.ThrottledOneItem
import com.intellij.ide.rpc.throttledWithAccumulation
import com.intellij.ide.vfs.rpcId
import com.intellij.ide.vfs.virtualFile
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.CheckedDisposable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ex.ProgressIndicatorEx
import com.intellij.platform.project.projectId
import com.intellij.platform.scopes.ScopeModelRemoteApi
import com.intellij.platform.util.coroutines.childScope
import com.intellij.usages.FindUsagesProcessPresentation
import com.intellij.usages.UsageInfo2UsageAdapter
import com.intellij.usages.UsageInfoAdapter
import com.intellij.util.cancelOnDispose
import fleet.rpc.client.RpcClientException
import fleet.rpc.client.RpcTimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus.Internal
import java.util.function.Consumer

private val LOG = logger<FindAndReplaceExecutorImpl>()

@Internal
open class FindAndReplaceExecutorImpl(val coroutineScope: CoroutineScope) : FindAndReplaceExecutor {
  private var validationJob: Job? = null
  private var findUsagesJob: Job? = null
  private var selectScopeJob: Job? = null
  private var currentSearchDisposable: CheckedDisposable? = null

  // The current search "session" scope on which result models load their preview content. It is parented to
  // the executor scope (NOT to the per-pass streaming job) so it survives an automatic load-more pass; it is
  // cancelled only when the session is superseded by a fresh search (see findUsages / isLoadMore). IJPL-247145.
  private var currentInitScope: CoroutineScope? = null

  override fun findUsages(
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
    isLoadMore: Boolean,
  ) {
    if (FindKey.isEnabled) {
      LOG.debug { "FiF: executor.findUsages entry; cancelling prevJob=$findUsagesJob shouldThrottle=$shouldThrottle maxUsages=$maxUsages isLoadMore=$isLoadMore" }

      // Cancel the previous streaming coroutine only. Model loads run on `currentInitScope` (a session scope
      // parented to the executor scope), so cancelling the streaming job no longer cancels in-flight loads;
      // that is what lets a load-more pass extend the session without stranding the previous pass's models.
      findUsagesJob?.cancel("new find request is started")

      findUsagesJob = coroutineScope.launch {
        var firstLogged = false
        try {
          LOG.debug { "FiF: coroutine start; selectScope join begin (selectScopeJob=$selectScopeJob)" }
          selectScopeJob?.join()
          LOG.debug { "FiF: selectScope join done" }
          val filesToScanInitially = previousUsages.mapNotNull { (it as? UsageInfoModel)?.model?.fileId?.virtualFile() }.toSet()

          // A load-more pass EXTENDS the current session: reuse its disposable + model-load scope so the
          // previous pass's still-loading models (e.g. the auto-selected first row) keep loading. A fresh
          // search supersedes: dispose the old session (cancels its models) and start a new scope. IJPL-247145.
          val reuseSession = isLoadMore &&
                             currentSearchDisposable?.isDisposed == false &&
                             currentInitScope?.isActive == true
          val searchDisposable: CheckedDisposable
          val initScope: CoroutineScope
          if (reuseSession) {
            searchDisposable = currentSearchDisposable!!
            initScope = currentInitScope!!
          }
          else {
            currentSearchDisposable?.let { Disposer.dispose(it) }
            val newDisposable = Disposer.newCheckedDisposable("Find in Project Search")
            if (!Disposer.tryRegister(disposableParent, newDisposable)) {
              Disposer.dispose(newDisposable)
              LOG.warn("Failed to register disposable for search. Looks like FindPopup is already closed. Search will be canceled.")
              return@launch
            }

            // Parent to the executor scope (not `this` streaming job) so the session survives load-more passes.
            val newScope = coroutineScope.childScope("FindAndReplaceExecutorImpl.UsageInit")
            Disposer.register(newDisposable) {
              newScope.cancel("search disposed")
            }

            currentSearchDisposable = newDisposable
            currentInitScope = newScope
            searchDisposable = newDisposable
            initScope = newScope
          }
          FindRemoteApi.getInstance().findByModel(
            findModel = findModel,
            projectId = project.projectId(),
            filesToScanInitially = filesToScanInitially.map { it.rpcId() },
            maxUsagesCount = maxUsages
          ).take(maxUsages)
            .let {
              if (shouldThrottle) it.throttledWithAccumulation()
              else it.map { event -> ThrottledOneItem(event) }
            }
            .collect { throttledItems ->
              if (searchDisposable.isDisposed) {
                return@collect
              }
              if (!firstLogged) {
                firstLogged = true
                LOG.debug { "FiF: first collected item batch (size=${throttledItems.items.size})" }
              }
              throttledItems.items.forEach { item ->
                val usage = UsageInfoModel.createUsageInfoModel(project, item, initScope, onUpdateModelCallback)
                if (!Disposer.tryRegister(searchDisposable, usage)) {
                  Disposer.dispose(usage)
                  return@collect
                }

                val shouldContinue = onResult(usage)
                if (!shouldContinue) {
                  return@collect
                }
              }
            }
          LOG.debug { "FiF: collect completed normally" }
        }
        catch (ce: CancellationException) {
          // A superseded/closed search generation is cancelled here. Re-throw to honour structured
          // concurrency; the finally below still fires the terminal callback so the popup is never
          // left stuck on "Searching…". The callback is generation-guarded on the caller side, so a
          // superseded search becomes a no-op there.
          LOG.debug { "FiF: coroutine CANCELLED: ${ce.message}" }
          throw ce
        }
        finally {
          // Always notify that this search generation has finished — including on cancellation or an
          // early return above — so the Find popup is never left stuck showing "Searching…" with no
          // results.
          LOG.debug { "FiF: coroutine finally -> onFinish()" }
          onFinish()
        }
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
    }
    else {
      if (findModel.isReplaceState) {
        ReplaceInProjectManager.getInstance(project).replaceInPath(findModel)
      }
      else {
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
      } catch (ex: RpcClientException) {
        LOG.warn("Unable to check directory", ex)
        onFinish(false)
      }
    }
  }

  override fun performScopeSelection(scopeId: String, project: Project) {
    selectScopeJob = coroutineScope.launch {
      val deferred = try {
       ScopeModelRemoteApi.getInstance().performScopeSelection(scopeId, project.projectId())
      }
      catch (e: RpcTimeoutException) {
        LOG.warn("Failed to select scope", e)
        null
      }
      deferred?.cancelOnDispose(project)
      deferred?.await()
    }
  }

  override fun cancelActivities() {
    val message = "cancel all activities for find and replace executor"
    validationJob?.cancel(message)
    findUsagesJob?.cancel(message)
    selectScopeJob?.cancel(message)
  }
}