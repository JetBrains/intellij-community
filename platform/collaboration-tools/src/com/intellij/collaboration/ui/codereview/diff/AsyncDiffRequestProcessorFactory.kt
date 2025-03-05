// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.diff

import com.intellij.collaboration.async.collectScoped
import com.intellij.collaboration.async.launchNow
import com.intellij.collaboration.ui.codereview.diff.model.AsyncDiffViewModel
import com.intellij.collaboration.ui.codereview.diff.model.CodeReviewDiffProcessorViewModel
import com.intellij.collaboration.ui.codereview.diff.model.DiffViewerScrollRequest
import com.intellij.collaboration.ui.codereview.diff.model.DiffViewerScrollRequestProducer
import com.intellij.collaboration.ui.util.selectedItem
import com.intellij.collaboration.util.*
import com.intellij.diff.chains.DiffRequestProducer
import com.intellij.diff.impl.DiffRequestProcessor
import com.intellij.diff.requests.DiffRequest
import com.intellij.diff.requests.ErrorDiffRequest
import com.intellij.diff.requests.LoadingDiffRequest
import com.intellij.diff.requests.NoDiffRequest
import com.intellij.diff.tools.combined.*
import com.intellij.openapi.ListSelection
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolder
import com.intellij.openapi.vcs.changes.ui.PresentableChange
import com.intellij.util.cancelOnDispose
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull

object AsyncDiffRequestProcessorFactory {
  //region Classic Diff
  fun <VM, C> createIn(
    cs: CoroutineScope, project: Project,
    diffVmFlow: Flow<VM?>,
    @RequiresEdt createContext: (VM) -> List<KeyValuePair<*>>,
    @RequiresEdt changePresenter: (C) -> PresentableChange,
  ): DiffRequestProcessor
    where VM : CodeReviewDiffProcessorViewModel<C>,
          C : AsyncDiffViewModel {
    val processor = MutableDiffRequestProcessor(project)
    cs.launchNow(CoroutineName("Code Review Diff UI")) {
      diffVmFlow.collectScoped { vm ->
        if (vm != null) {
          withContext(Dispatchers.Main.immediate) {
            val context = createContext(vm)
            try {
              context.forEach { processor.putData(it) }
              handleChanges(vm, processor, changePresenter)
            }
            finally {
              // needs NCS because editor release happens on viewer disposal
              withContext(NonCancellable) {
                processor.applyRequest(NoDiffRequest.INSTANCE)
              }
              context.forEach(processor::clearData)
            }
          }
        }
      }
    }.cancelOnDispose(processor)
    return processor
  }

  private suspend fun <C : AsyncDiffViewModel> handleChanges(
    diffVm: CodeReviewDiffProcessorViewModel<C>,
    processor: MutableDiffRequestProcessor,
    changePresenter: (C) -> PresentableChange,
  ): Nothing {
    diffVm.changes.collectScoped { result ->
      result?.onInProgress {
        delay(DiffUIUtil.PROGRESS_DISPLAY_DELAY)
        processor.applyRequest(LoadingDiffRequest())
      }?.onFailure {
        processor.applyRequest(ErrorDiffRequest(it))
      }?.onSuccess { state ->
        handleState(diffVm, processor, state, changePresenter)
      } ?: run {
        processor.applyRequest(NoDiffRequest.INSTANCE)
      }
    }
    awaitCancellation()
  }

  private suspend fun <C : AsyncDiffViewModel> handleState(
    processorVm: CodeReviewDiffProcessorViewModel<C>,
    processor: MutableDiffRequestProcessor,
    state: CodeReviewDiffProcessorViewModel.State<C>,
    changePresenter: (C) -> PresentableChange,
  ) {
    val diffVm = state.selectedChanges.selectedItem
    if (diffVm == null) {
      processor.applyRequest(NoDiffRequest.INSTANCE)
      return
    }
    try {
      processor.navigator = StateNavigator(processorVm, state, changePresenter)
      val vm = if (state is DiffViewerScrollRequestProducer) {
        ScrollableAsyncDiffViewModel(diffVm, state)
      }
      else diffVm
      processor.showDiff(vm)
    }
    finally {
      processor.navigator = MutableDiffRequestProcessor.Navigator.empty<C>()
    }
  }

  private class StateNavigator<C : Any>(
    private val processorVm: CodeReviewDiffProcessorViewModel<C>,
    private val state: CodeReviewDiffProcessorViewModel.State<C>,
    private val changePresenter: (C) -> PresentableChange,
  ) : MutableDiffRequestProcessor.Navigator<C> {
    override fun getCurrentList(): ListSelection<C> = state.selectedChanges
    override fun selectPrev(fromDifferences: Boolean) {
      val newIdx = state.selectedChanges.run {
        if (selectedIndex <= 0) null else selectedIndex - 1
      }
      if (newIdx != null) {
        val scrollCommand =
          if (fromDifferences) DiffViewerScrollRequest.toLastChange()
          else null
        processorVm.showChange(newIdx, scrollCommand)
      }
    }

    override fun selectNext(fromDifferences: Boolean) {
      val newIdx = state.selectedChanges.run {
        if (selectedIndex !in 0 until list.lastIndex) null
        else selectedIndex + 1
      }
      if (newIdx != null) {
        val scrollCommand =
          if (fromDifferences) DiffViewerScrollRequest.toFirstChange()
          else null
        processorVm.showChange(newIdx, scrollCommand)
      }
    }

    override fun select(change: C) = processorVm.showChange(change)

    override fun getChangePresentation(change: C): PresentableChange = changePresenter(change)
  }

  private class ScrollableAsyncDiffViewModel(
    original: AsyncDiffViewModel,
    scrollRequestProducer: DiffViewerScrollRequestProducer,
  ) : AsyncDiffViewModel by original, DiffViewerScrollRequestProducer by scrollRequestProducer
  //endregion

  //region Combined Diff
  fun <VM, C> createCombinedIn(
    cs: CoroutineScope, project: Project,
    reviewDiffVm: Flow<VM?>,
    @RequiresEdt createContext: (VM) -> List<KeyValuePair<*>>,
    @RequiresEdt changeVmPresenter: (C) -> PresentableChange,
  ): CombinedDiffComponentProcessor
    where VM : CodeReviewDiffProcessorViewModel<C>,
          C : AsyncDiffViewModel {
    val processor = CombinedDiffManager.getInstance(project).createProcessor()
    cs.launchNow(CoroutineName("Code Review Combined Diff UI")) {
      reviewDiffVm.collectLatest { diffVm ->
        if (diffVm != null) {
          withContext(Dispatchers.Main.immediate) {
            val context = createContext(diffVm)
            try {
              context.forEach { processor.context.putData(it) }
              handleChanges(diffVm, processor, changeVmPresenter)
              awaitCancellation()
            }
            finally {
              context.forEach(processor.context::clearData)
              processor.cleanBlocks()
            }
          }
        }
      }
    }.cancelOnDispose(processor.disposable)
    return processor
  }

  private suspend fun <C : AsyncDiffViewModel> handleChanges(
    diffVm: CodeReviewDiffProcessorViewModel<C>,
    processor: CombinedDiffComponentProcessor,
    changePresenter: (C) -> PresentableChange,
  ) {
    diffVm.changes.collectLatest { result ->
      result?.onInProgress {
        delay(DiffUIUtil.PROGRESS_DISPLAY_DELAY)
        processor.setNewBlocks(listOf(CombinedBlockProducer(CONSTANT_BLOCK_ID, DiffUIUtil.LOADING_PRODUCER)))
      }?.onFailure {
        processor.setNewBlocks(listOf(CombinedBlockProducer(CONSTANT_BLOCK_ID, DiffUIUtil.createErrorProducer(it))))
      }?.onSuccess { state ->
        handleState(processor, state, changePresenter)
      } ?: run {
        processor.setNewBlocks(emptyList())
      }
    }
  }

  private fun CombinedDiffComponentProcessor.setNewBlocks(blocks: List<CombinedBlockProducer>?) {
    cleanBlocks()
    setBlocks(blocks.orEmpty())
  }

  private fun <C : AsyncDiffViewModel> handleState(
    processor: CombinedDiffComponentProcessor,
    state: CodeReviewDiffProcessorViewModel.State<C>,
    changePresenter: (C) -> PresentableChange,
  ) {
    val vms = state.selectedChanges.list.associateBy {
      val presentation = changePresenter(it)
      CombinedPathBlockId(presentation.filePath, presentation.fileStatus)
    }
    val current = processor.blocks.map { it.id }
    if (current.size != vms.size || !current.containsAll(vms.keys)) {
      val blocks = mutableListOf<CombinedBlockProducer>()
      for ((id, vm) in vms) {
        blocks += CombinedBlockProducer(id, vm.asProducer(id))
      }
      processor.setNewBlocks(blocks)
    }

    // fixme: fix after selection rework
    val selectedChange = state.selectedChanges.selectedItem
    if (selectedChange != null) {
      val presentation = changePresenter(selectedChange)
      val selectedBlock = CombinedPathBlockId(presentation.filePath, presentation.fileStatus)
      processor.context.getUserData(COMBINED_DIFF_VIEWER_KEY)?.selectDiffBlock(selectedBlock, focusBlock = false)
    }
  }

  private fun AsyncDiffViewModel.asProducer(id: CombinedPathBlockId): DiffRequestProducer {
    return object : DiffRequestProducer {
      override fun getName(): String = id.path.path

      override fun process(context: UserDataHolder, indicator: ProgressIndicator): DiffRequest =
        runBlockingCancellable {
          request.mapNotNull { it?.result }.first().getOrThrow()
        }
    }
  }
  //endregion
}