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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus.Internal
import java.util.function.Consumer

private val LOG = logger<FindAndReplaceExecutorImpl>()

@Internal
open class FindAndReplaceExecutorImpl(val coroutineScope: CoroutineScope) : FindAndReplaceExecutor {
  @Volatile private var validationJob: Job? = null
  @Volatile private var selectScopeJob: Job? = null

  // A single command channel + single consumer coroutine
  private val requests = Channel<FindCommand>(capacity = Channel.UNLIMITED)

  init {
    val processor = coroutineScope.launch { requestProcessor() }
    // Once the processor stops (service scope canceled, e.g. project close), close the channel so any later
    // trySend fails fast and findUsages can fire its terminal callback instead of dropping the request silently.
    processor.invokeOnCompletion { requests.close() }
  }

  private suspend fun requestProcessor() {
    var currentSession: Session? = null
    var currentPass: Job? = null
    for (command in requests) {
      when (command) {
        is StartSearch -> {
          LOG.debug { "FiF: processing StartSearch loadMore=${command.isLoadMore} maxUsages=${command.maxUsages}" }
          // Cancel the previous streaming pass only.
          currentPass?.cancel("new find request is started")

          // A load-more pass EXTENDS the current session; a fresh search supersedes it. Both `currentSession`
          // and the liveness checks are evaluated on this single coroutine, so there is no TOCTOU race.
          val existing = currentSession
          val session: Session

          if (command.isLoadMore && existing != null && !existing.disposable.isDisposed && existing.initScope.isActive) {
            session = existing
          }
          else {
            currentSession?.let { Disposer.dispose(it.disposable) }
            val newDisposable = Disposer.newCheckedDisposable("Find in Project Search")

            if (!Disposer.tryRegister(command.disposableParent, newDisposable)) {
              Disposer.dispose(newDisposable)
              LOG.warn("Failed to register disposable for search. Looks like FindPopup is already closed. Search will be canceled.")
              // The popup is gone, but still fire the terminal callback so nothing is left waiting.
              command.onFinish()
              continue
            }

            // Parent to the executor scope (not the pass) so the session survives load-more passes.
            val newScope = coroutineScope.childScope("FindAndReplaceExecutorImpl.UsageInit")

            Disposer.register(newDisposable) {
              newScope.cancel("search disposed")
            }

            session = Session(newDisposable, newScope)
            currentSession = session
          }

          currentPass = launchSearchPass(command, session)
        }
        CancelSearch -> {
          LOG.debug { "FiF: processing CancelSearch" }
          currentPass?.cancel("cancel all activities for find and replace executor")
        }
      }
    }
  }

  /**
   * Launches the streaming pass on [coroutineScope] (a sibling of the model-load coroutines, not a child of the
   * session scope) so that cancelling the pass — e.g. when superseded by the next keystroke — never cancels the
   * in-flight model loads of a reused session.
   */
  private fun launchSearchPass(command: StartSearch, session: Session): Job {
    return coroutineScope.launch {
      var firstLogged = false
      try {
        LOG.debug { "FiF: pass start; selectScope join begin (selectScopeJob=$selectScopeJob)" }
        selectScopeJob?.join()
        LOG.debug { "FiF: selectScope join done" }

        val filesToScanInitially = command.previousUsages
          .mapNotNull { (it as? UsageInfoModel)?.model?.fileId?.virtualFile() }
          .toSet()

        FindRemoteApi.getInstance().findByModel(
          findModel = command.findModel,
          projectId = command.project.projectId(),
          filesToScanInitially = filesToScanInitially.map { it.rpcId() },
          maxUsagesCount = command.maxUsages
        ).take(command.maxUsages)
          .let {
            if (command.shouldThrottle) it.throttledWithAccumulation()
            else it.map { event -> ThrottledOneItem(event) }
          }
          .collect { throttledItems ->
            if (session.disposable.isDisposed) {
              return@collect
            }

            if (!firstLogged) {
              firstLogged = true
              LOG.debug { "FiF: first collected item batch (size=${throttledItems.items.size})" }
            }

            throttledItems.items.forEach { item ->
              val usage = UsageInfoModel.createUsageInfoModel(command.project, item, session.initScope, command.onUpdateModelCallback)
              if (!Disposer.tryRegister(session.disposable, usage)) {
                Disposer.dispose(usage)
                return@collect
              }

              val shouldContinue = command.onResult(usage)
              if (!shouldContinue) {
                return@collect
              }
            }
          }
        LOG.debug { "FiF: collect completed normally" }
      }
      catch (ce: CancellationException) {
        // A superseded/closed search pass is cancelled here. Re-throw to honour structured concurrency; the
        // finally below still fires the terminal callback so the popup is never left stuck on "Searching…".
        // The callback is generation-guarded on the caller side, so a superseded search becomes a no-op there.
        LOG.debug { "FiF: pass CANCELLED: ${ce.message}" }
        throw ce
      }
      finally {
        // Always notify that this search pass has finished — including on cancellation or an early return above —
        // so the Find popup is never left stuck showing "Searching…" with no results.
        LOG.debug { "FiF: pass finally -> onFinish()" }
        command.onFinish()
      }
    }
  }

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
      LOG.debug { "FiF: executor.findUsages entry; shouldThrottle=$shouldThrottle maxUsages=$maxUsages isLoadMore=$isLoadMore" }
      val command = StartSearch(
        project, findModel, previousUsages, shouldThrottle, disposableParent,
        onUpdateModelCallback, onResult, onFinish, maxUsages, isLoadMore,
      )

      if (requests.trySend(command).isFailure) {
        LOG.debug { "FiF: request channel closed; firing onFinish immediately" }
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
    selectScopeJob?.cancel(message)
    // Route the search cancellation through the same channel so it is applied serially against the session state.
    requests.trySend(CancelSearch)
  }

  private sealed interface FindCommand

  private class StartSearch(
    val project: Project,
    val findModel: FindModel,
    val previousUsages: Set<UsageInfoAdapter>,
    val shouldThrottle: Boolean,
    val disposableParent: Disposable,
    val onUpdateModelCallback: Consumer<UsageInfoAdapter>,
    val onResult: (UsageInfoAdapter) -> Boolean,
    val onFinish: () -> Unit?,
    val maxUsages: Int,
    val isLoadMore: Boolean,
  ) : FindCommand

  private object CancelSearch : FindCommand

  // A search "session": the disposable that owns the pass's result models and the scope on which those models
  // load their preview content. It survives automatic load-more passes and is superseded only by a fresh search.
  private class Session(val disposable: CheckedDisposable, val initScope: CoroutineScope)
}
