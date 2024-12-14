// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.diff

import com.intellij.collaboration.async.collectScoped
import com.intellij.collaboration.async.launchNow
import com.intellij.collaboration.ui.codereview.diff.model.ComputedDiffViewModel
import com.intellij.collaboration.ui.codereview.diff.model.DiffProducersViewModel
import com.intellij.collaboration.ui.codereview.diff.model.getSelected
import com.intellij.diff.FrameDiffTool
import com.intellij.diff.chains.DiffRequestProducer
import com.intellij.diff.impl.CacheDiffRequestProcessor
import com.intellij.diff.util.DiffUserDataKeysEx
import com.intellij.openapi.ListSelection
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.actions.diff.PresentableGoToChangePopupAction
import com.intellij.openapi.vcs.changes.ui.ChangeDiffRequestChain
import com.intellij.openapi.vcs.changes.ui.PresentableChange
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@ApiStatus.Obsolete
class ComputingDiffRequestProcessor(project: Project, cs: CoroutineScope, private val vm: ComputedDiffViewModel)
  : CacheDiffRequestProcessor.Simple(project) {

  private var currentProducerFromUpdate: DiffRequestProducer? = null

  init {
    cs.launchNow {
      withContext(Dispatchers.Main) {
        vm.diffVm.collectScoped {
          val result = it.result
          if (result == null) {
            delay(DiffUIUtil.PROGRESS_DISPLAY_DELAY)
            reloadRequest()
          }
          else {
            result.getOrNull()?.let { vm -> installProducers(vm) } ?: updateRequest()
          }
        }
      }
    }
  }

  private suspend fun installProducers(vm: DiffProducersViewModel) {
    vm.producers.map {
      it.getSelected()
    }.distinctUntilChanged().collectScoped { producer ->
      updateRequest()
      if (producer is ScrollableDiffRequestProducer) {
        launchNow {
          producer.scrollRequests.collect {
            activeViewer?.scrollTo(it)
          }
        }
      }
    }
  }

  private fun FrameDiffTool.DiffViewer.scrollTo(loc: DiffLineLocation) {
    DiffViewerScrollRequestProcessor.scroll(this, loc)
  }

  override fun getCurrentRequestProvider(): DiffRequestProducer? {
    val producersVmResult = vm.diffVm.value.result
    return if (producersVmResult == null) {
      DiffUIUtil.LOADING_PRODUCER
    }
    else {
      producersVmResult.fold({ it?.producers?.value?.getSelected() }, DiffUIUtil::createErrorProducer)
    }
  }

  override fun updateRequest(force: Boolean, useCache: Boolean, scrollToChangePolicy: DiffUserDataKeysEx.ScrollToPolicy?) {
    // do not reload the request if the provider hasn't changed
    val producerToSet = currentRequestProvider
    if (useCache && !force && currentProducerFromUpdate == producerToSet) return
    super.updateRequest(force, useCache, scrollToChangePolicy)
    currentProducerFromUpdate = producerToSet
  }

  private val currentVm: DiffProducersViewModel?
    get() = vm.diffVm.value.result?.getOrNull()

  override fun isNavigationEnabled(): Boolean {
    val producersVm = currentVm ?: return false
    return producersVm.canNavigate()
  }

  override fun hasPrevChange(fromUpdate: Boolean): Boolean =
    currentVm?.canSelectPrev() ?: false

  override fun hasNextChange(fromUpdate: Boolean): Boolean =
    currentVm?.canSelectNext() ?: false

  override fun goToNextChange(fromDifferences: Boolean) =
    goToNextChangeImpl(fromDifferences) {
      currentVm?.selectNext()
    }

  override fun goToPrevChange(fromDifferences: Boolean) =
    goToPrevChangeImpl(fromDifferences) {
      currentVm?.selectPrev()
    }

  override fun createGoToChangeAction(): AnAction = MyGoToChangePopupAction()

  private inner class MyGoToChangePopupAction : PresentableGoToChangePopupAction<ChangeDiffRequestChain.Producer>() {
    override fun getChanges(): ListSelection<out ChangeDiffRequestChain.Producer> =
      currentVm?.producers?.value?.run {
        ListSelection.createAt(producers, selectedIdx)
      } ?: ListSelection.empty()

    override fun onSelected(change: ChangeDiffRequestChain.Producer) {
      currentVm?.select(change)
    }

    override fun getPresentation(change: ChangeDiffRequestChain.Producer): PresentableChange = change
  }
}
