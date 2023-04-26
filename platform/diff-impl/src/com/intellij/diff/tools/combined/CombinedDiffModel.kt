// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.tools.combined

import com.intellij.diff.DiffContext
import com.intellij.diff.chains.DiffRequestProducer
import com.intellij.diff.requests.DiffRequest
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.CheckedDisposable
import com.intellij.util.concurrency.annotations.RequiresEdt
import java.util.*


interface CombinedDiffModel {
  val haveParentDisposable: Boolean
  val ourDisposable: CheckedDisposable
  val context: DiffContext
  val requests: Map<CombinedBlockId, DiffRequestProducer>

  fun cleanBlocks()
  fun reload()

  /**
   * Updates current model with the new requests
   */
  fun setBlocks(requests: Map<CombinedBlockId, DiffRequestProducer>)

  fun addBlock(blockId: CombinedBlockId, diffRequestProducer: DiffRequestProducer, position: InsertPosition)
  fun removeBlock(blockId: CombinedBlockId)

  fun getCurrentRequest(): DiffRequest?

  fun getLoadedRequests(): List<DiffRequest>

  fun loadRequestContents(blockIds: Collection<CombinedBlockId>, blockToSelect: CombinedBlockId?)
  fun unloadRequestContents(blockIds: Collection<CombinedBlockId>)

  fun addListener(listener: CombinedDiffModelListener, disposable: Disposable)

  data class RequestData(val blockId: CombinedBlockId, val producer: DiffRequestProducer)
  data class InsertPosition(val blockId: CombinedBlockId, val above: Boolean)
}

interface CombinedDiffModelListener : EventListener {
  fun onModelReset()

  @RequiresEdt
  fun onRequestsLoaded(requests: Map<CombinedBlockId, DiffRequest>, blockIdToSelect: CombinedBlockId? = null)

  @RequiresEdt
  fun onRequestContentsUnloaded(requests: Map<CombinedBlockId, DiffRequest>)

  fun onProgressBar(visible: Boolean)
}