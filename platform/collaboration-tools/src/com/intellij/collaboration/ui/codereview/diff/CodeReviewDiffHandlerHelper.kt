// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.diff

import com.intellij.collaboration.async.cancelledWith
import com.intellij.collaboration.async.launchNow
import com.intellij.collaboration.ui.codereview.diff.model.ComputedDiffViewModel
import com.intellij.collaboration.ui.codereview.diff.model.DiffProducersViewModel
import com.intellij.collaboration.ui.codereview.diff.model.getSelected
import com.intellij.collaboration.util.ComputedResult
import com.intellij.collaboration.util.KeyValuePair
import com.intellij.collaboration.util.clearData
import com.intellij.collaboration.util.putData
import com.intellij.diff.impl.DiffRequestProcessor
import com.intellij.diff.tools.combined.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.LocalFilePath
import com.intellij.platform.util.coroutines.childScope
import com.intellij.util.cancelOnDispose
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import org.jetbrains.annotations.ApiStatus

/**
 * Can be used as a delegate for a service which is supposed to provide diff UI
 *
 * [AsyncDiffRequestProcessorFactory] should be used instead
 */
@ApiStatus.Obsolete
class CodeReviewDiffHandlerHelper(private val project: Project, parentCs: CoroutineScope) {
  private val cs = parentCs.childScope(javaClass.name, Dispatchers.Main.immediate)

  fun <VM : ComputedDiffViewModel> createDiffRequestProcessor(
    reviewDiffVm: Flow<VM?>, createContext: (VM) -> List<KeyValuePair<*>>
  ): DiffRequestProcessor {
    val uiCs = cs.childScope("Code Review Diff UI")
    val vm = object : ComputedDiffViewModel {
      override val diffVm = MutableStateFlow<ComputedResult<DiffProducersViewModel?>>(ComputedResult.loading())
    }
    val processor = ComputingDiffRequestProcessor(project, uiCs, vm)
    uiCs.launchNow {
      reviewDiffVm.collectLatest { computedDiffVm ->
        if (computedDiffVm != null) {
          val context = createContext(computedDiffVm)
          try {
            context.forEach { processor.putData(it) }
            computedDiffVm.diffVm.collect(vm.diffVm)
          }
          finally {
            context.forEach(processor::clearData)
            vm.diffVm.value = ComputedResult.loading()
          }
        }
      }
    }
    uiCs.cancelledWith(processor)
    return processor
  }

  fun <VM : ComputedDiffViewModel> createCombinedDiffModel(
    reviewDiffVm: Flow<VM?>, createContext: (VM) -> List<KeyValuePair<*>>
  ): CombinedDiffComponentProcessor {
    val processor = CombinedDiffManager.getInstance(project).createProcessor()
    cs.launchNow(CoroutineName("Code Review Combined Diff UI")) {
      reviewDiffVm.collectLatest { computedDiffVm ->
        if (computedDiffVm != null) {
          val context = createContext(computedDiffVm)
          try {
            context.forEach { processor.context.putData(it) }
            handleChanges(computedDiffVm, processor)
            awaitCancellation()
          }
          finally {
            context.forEach(processor.context::clearData)
            processor.cleanBlocks()
          }
        }
      }
    }.cancelOnDispose(processor.disposable)
    return processor
  }

  private suspend fun handleChanges(computedDiffVm: ComputedDiffViewModel, processor: CombinedDiffComponentProcessor) {
    fun setBlocks(blocks: List<CombinedBlockProducer>?) {
      processor.cleanBlocks()
      processor.setBlocks(blocks.orEmpty())
    }

    computedDiffVm.diffVm.collectLatest { result ->
      if (result.isInProgress) {
        delay(DiffUIUtil.PROGRESS_DISPLAY_DELAY)
        setBlocks(listOf(CombinedBlockProducer(CONSTANT_BLOCK_ID, DiffUIUtil.LOADING_PRODUCER)))
        return@collectLatest
      }

      val diffVm = result.result?.getOrElse {
        setBlocks(listOf(CombinedBlockProducer(CONSTANT_BLOCK_ID, DiffUIUtil.createErrorProducer(it))))
        return@collectLatest
      }

      if (diffVm == null) {
        setBlocks(null)
        return@collectLatest
      }

      processor.installVm(diffVm)
    }
  }

  // fixme: fix after selection rework
  private suspend fun CombinedDiffComponentProcessor.installVm(vm: DiffProducersViewModel) {
    vm.producers.collectLatest {
      val current = blocks.map { it.producer }
      val new = it.producers
      if (current.size != new.size || !current.containsAll(new)) {
        val blocks = mutableListOf<CombinedBlockProducer>()
        for (producer in new) {
          val id = CombinedPathBlockId(producer.filePath, producer.fileStatus)
          blocks += CombinedBlockProducer(id, producer)
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

internal val CONSTANT_BLOCK_ID = CombinedPathBlockId(LocalFilePath("/", false), null)

internal fun <T> DiffRequestProcessor.putData(keyValue: KeyValuePair<T>) {
  putContextUserData(keyValue.key, keyValue.value)
}

internal fun DiffRequestProcessor.clearData(keyValue: KeyValuePair<*>) {
  putContextUserData(keyValue.key, null)
}