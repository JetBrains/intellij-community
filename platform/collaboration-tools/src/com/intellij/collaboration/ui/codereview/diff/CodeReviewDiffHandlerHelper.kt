// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.diff

import com.intellij.collaboration.async.cancelledWith
import com.intellij.collaboration.async.launchNow
import com.intellij.collaboration.ui.codereview.diff.model.ComputedDiffViewModel
import com.intellij.collaboration.ui.codereview.diff.model.DiffProducersViewModel
import com.intellij.collaboration.ui.codereview.diff.model.getSelected
import com.intellij.collaboration.util.ComputedResult
import com.intellij.diff.chains.DiffRequestProducer
import com.intellij.diff.impl.DiffRequestProcessor
import com.intellij.diff.tools.combined.COMBINED_DIFF_VIEWER_KEY
import com.intellij.diff.tools.combined.CombinedBlockId
import com.intellij.diff.tools.combined.CombinedDiffModelImpl
import com.intellij.diff.tools.combined.CombinedPathBlockId
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vcs.LocalFilePath
import com.intellij.platform.util.coroutines.childScope
import com.intellij.util.cancelOnDispose
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest

/**
 * Can be used as a delegate for a service which is supposed to provide diff UI
 */
class CodeReviewDiffHandlerHelper(private val project: Project, parentCs: CoroutineScope) {
  private val cs = parentCs.childScope(Dispatchers.Main.immediate)

  fun <VM : ComputedDiffViewModel> createDiffRequestProcessor(reviewDiffVm: Flow<VM?>, vmKey: Key<VM>): DiffRequestProcessor {
    val uiCs = cs.childScope()
    val vm = object : ComputedDiffViewModel {
      override val diffVm = MutableStateFlow<ComputedResult<DiffProducersViewModel?>>(ComputedResult.loading())
    }
    val processor = ComputingDiffRequestProcessor(project, uiCs, vm)
    uiCs.launchNow(CoroutineName("Code Review Diff UI")) {
      reviewDiffVm.collectLatest { computedDiffVm ->
        try {
          if (computedDiffVm != null) {
            processor.putContextUserData(vmKey, computedDiffVm)
            computedDiffVm.diffVm.collect(vm.diffVm)
          }
        }
        finally {
          processor.putContextUserData(vmKey, null)
        }
      }
    }
    uiCs.cancelledWith(processor)
    return processor
  }

  fun <VM : ComputedDiffViewModel> createCombinedDiffModel(reviewDiffVm: Flow<VM?>, vmKey: Key<VM>): CombinedDiffModelImpl {
    val model = CombinedDiffModelImpl(project, null)
    cs.launchNow(CoroutineName("Code Review Combined Diff UI")) {
      reviewDiffVm.collectLatest { computedDiffVm ->
        try {
          if (computedDiffVm != null) {
            model.context.putUserData(vmKey, computedDiffVm)
            handleChanges(computedDiffVm, model)
            awaitCancellation()
          }
        }
        finally {
          model.context.putUserData(vmKey, null)
          model.cleanBlocks()
        }
      }
    }.cancelOnDispose(model.ourDisposable)
    return model
  }

  private suspend fun handleChanges(computedDiffVm: ComputedDiffViewModel, model: CombinedDiffModelImpl) {
    fun setBlocks(blocks: Map<CombinedBlockId, DiffRequestProducer>?) {
      model.cleanBlocks()
      model.setBlocks(blocks.orEmpty())
    }

    computedDiffVm.diffVm.collectLatest { result ->
      if (result.isInProgress) {
        delay(DiffUIUtil.PROGRESS_DISPLAY_DELAY)
        setBlocks(mapOf(CONSTANT_BLOCK_ID to DiffUIUtil.LOADING_PRODUCER))
        return@collectLatest
      }

      val diffVm = result.result?.getOrElse {
        setBlocks(mapOf(CONSTANT_BLOCK_ID to DiffUIUtil.createErrorProducer(it)))
        return@collectLatest
      }

      if (diffVm == null) {
        setBlocks(null)
        return@collectLatest
      }

      model.installVm(diffVm)
    }
  }

  // fixme: fix after selection rework
  private suspend fun CombinedDiffModelImpl.installVm(vm: DiffProducersViewModel) {
    vm.producers.collectLatest {
      val current = requests.values
      val new = it.producers
      if (current.size != new.size || !current.containsAll(new)) {
        val blocks = linkedMapOf<CombinedBlockId, DiffRequestProducer>()
        for (producer in new) {
          blocks[CombinedPathBlockId(producer.filePath, producer.fileStatus)] = producer
        }
        setBlocks(blocks)
      }

      val selectedChange = it.getSelected()
      if (selectedChange != null) {
        val selectedBlock = CombinedPathBlockId(selectedChange.filePath, selectedChange.fileStatus)
        context.getUserData(COMBINED_DIFF_VIEWER_KEY)?.selectDiffBlock(selectedBlock, focusBlock = false)
      }
    }
  }
}

private val CONSTANT_BLOCK_ID = CombinedPathBlockId(LocalFilePath("", false), null)