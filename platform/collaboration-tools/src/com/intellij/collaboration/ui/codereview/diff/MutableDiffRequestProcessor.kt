// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.diff

import com.intellij.collaboration.async.collectScoped
import com.intellij.collaboration.async.launchNow
import com.intellij.collaboration.ui.codereview.diff.model.AsyncDiffViewModel
import com.intellij.collaboration.ui.codereview.diff.model.DiffViewerScrollRequest
import com.intellij.collaboration.ui.codereview.diff.model.DiffViewerScrollRequestProducer
import com.intellij.collaboration.ui.codereview.diff.viewer.viewerReadyFlow
import com.intellij.collaboration.util.onFailure
import com.intellij.collaboration.util.onInProgress
import com.intellij.collaboration.util.onSuccess
import com.intellij.diff.impl.DiffRequestProcessor
import com.intellij.diff.requests.DiffRequest
import com.intellij.diff.requests.ErrorDiffRequest
import com.intellij.diff.requests.LoadingDiffRequest
import com.intellij.diff.requests.NoDiffRequest
import com.intellij.diff.tools.util.base.DiffViewerBase
import com.intellij.diff.util.DiffUserDataKeysEx.ScrollToPolicy
import com.intellij.openapi.ListSelection
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.progress.checkCanceled
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.actions.diff.PresentableGoToChangePopupAction
import com.intellij.openapi.vcs.changes.ui.PresentableChange
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.openapi.wm.awaitFocusSettlesDown
import com.intellij.util.EventDispatcher
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import java.util.*

/**
 * A [DiffRequestProcessor] whose state is controlled externally
 */
internal class MutableDiffRequestProcessor(project: Project) : DiffRequestProcessor(project) {
  private val updateSignalDispatcher = EventDispatcher.create(UpdateSignalListener::class.java)

  // after setting the navigator, the viewer should be re-created
  var navigator: Navigator<*> = Navigator.empty<Any>()

  fun applyRequest(request: DiffRequest) = super.doApplyRequest(request)

  override fun reloadRequest() =
    updateSignalDispatcher.multicaster.onReloadRequested()

  override fun updateRequest(force: Boolean, scrollToChangePolicy: ScrollToPolicy?) =
    updateSignalDispatcher.multicaster.onUpdateRequested(force, scrollToChangePolicy)

  fun addUpdateSignalListener(listener: UpdateSignalListener) = updateSignalDispatcher.addListener(listener)
  fun removeUpdateSignalListener(listener: UpdateSignalListener) = updateSignalDispatcher.removeListener(listener)

  private fun getCurrentList() = navigator.getCurrentList()
  override fun isNavigationEnabled(): Boolean = getCurrentList().list.isNotEmpty()
  override fun hasPrevChange(fromUpdate: Boolean): Boolean = getCurrentList().selectedIndex > 0
  override fun hasNextChange(fromUpdate: Boolean): Boolean = getCurrentList().run { selectedIndex < list.lastIndex }
  override fun goToNextChange(fromDifferences: Boolean) = navigator.selectNext(fromDifferences)
  override fun goToPrevChange(fromDifferences: Boolean) = navigator.selectPrev(fromDifferences)
  override fun createGoToChangeAction(): AnAction = navigator.getPopupAction()

  interface UpdateSignalListener : EventListener {
    fun onReloadRequested() {}
    fun onUpdateRequested(force: Boolean, scrollToChangePolicy: ScrollToPolicy?) {}
  }

  interface Navigator<C : Any> {
    fun getCurrentList(): ListSelection<C>
    fun selectNext(fromDifferences: Boolean)
    fun selectPrev(fromDifferences: Boolean)
    fun select(change: C)
    fun getChangePresentation(change: C): PresentableChange?

    companion object {
      fun <C : Any> empty(): Navigator<C> = object : Navigator<C> {
        override fun getCurrentList(): ListSelection<C> = ListSelection.empty()
        override fun selectNext(fromDifferences: Boolean) = Unit
        override fun selectPrev(fromDifferences: Boolean) = Unit
        override fun select(change: C) = Unit
        override fun getChangePresentation(change: C): PresentableChange? = null
      }
    }
  }

  private fun <C : Any> Navigator<C>.getPopupAction() = object : PresentableGoToChangePopupAction<C>() {
    override fun getChanges(): ListSelection<out C> = getCurrentList().let { ListSelection.createAt(it.list, it.selectedIndex) }
    override fun onSelected(change: C) = select(change)
    override fun getPresentation(change: C): PresentableChange? = getChangePresentation(change)
  }
}

internal suspend fun MutableDiffRequestProcessor.showDiff(diffVm: AsyncDiffViewModel): Nothing {
  diffVm.request.collectScoped { requestResult ->
    requestResult?.onInProgress {
      delay(DiffUIUtil.PROGRESS_DISPLAY_DELAY)
      applyRequestUpdateable(LoadingDiffRequest())
    }?.onSuccess {
      applyRequestUpdateable(it) {
        if (diffVm is DiffViewerScrollRequestProducer) {
          handleScrolling(diffVm)
        }
      }
    }?.onFailure {
      applyRequestUpdateable(ErrorDiffRequest(it))
    } ?: run {
      applyRequestUpdateable(NoDiffRequest.INSTANCE)
    }
    diffVm.reloadRequest()
  }
  awaitCancellation()
}

/**
 * Apply the request and suspend until the update or reload signal is received
 * In case of reload the function exits, and in case of update the request is re-applied
 */
private suspend fun MutableDiffRequestProcessor.applyRequestUpdateable(
  request: DiffRequest,
  inRequestScope: suspend () -> Unit = {},
) {
  withContext(Dispatchers.Main.immediate) {
    while (true) {
      checkCanceled()
      val needReload = supervisorScope {
        IdeFocusManager.getInstance(project).awaitFocusSettlesDown()
        checkCanceled()
        applyRequest(request)
        val sideJob = launchNow {
          inRequestScope()
        }
        awaitUpdateSignal().also {
          sideJob.cancelAndJoin()
        }
      }
      if (needReload) break
    }
  }
}

private suspend fun MutableDiffRequestProcessor.awaitUpdateSignal(): Boolean =
  callbackFlow {
    val listener = object : MutableDiffRequestProcessor.UpdateSignalListener {
      /**
       * [scrollToChangePolicy] is NOT null only when invoked from navigation popup, but in this case we handle scrolling asynchronously,
       * so it's fine to ignore this parameter altogether
       * Also since the VM now controls the current request, there's not much sense to react to non-forced update
       * since it will be handled by state collector
       */
      override fun onUpdateRequested(force: Boolean, scrollToChangePolicy: ScrollToPolicy?) {
        if (force) {
          trySend(false)
        }
      }

      override fun onReloadRequested() {
        trySend(true)
      }
    }
    addUpdateSignalListener(listener)
    awaitClose {
      removeUpdateSignalListener(listener)
    }
  }.first()

private suspend fun MutableDiffRequestProcessor.handleScrolling(producer: DiffViewerScrollRequestProducer): Nothing? {
  val viewer = activeViewer
  if (viewer is DiffViewerBase) {
    producer.scrollRequests.collect {
      viewer.executeScroll(it)
    }
  }
  return null
}

private suspend fun DiffViewerBase.executeScroll(cmd: DiffViewerScrollRequest) {
  val v = this
  withContext(Dispatchers.Main.immediate) {
    viewerReadyFlow().first { it }
    DiffViewerScrollRequestProcessor.scroll(v, cmd)
  }
}