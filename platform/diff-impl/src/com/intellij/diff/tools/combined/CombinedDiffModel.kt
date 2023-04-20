// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.tools.combined

import com.intellij.diff.DiffContext
import com.intellij.diff.chains.DiffRequestProducer
import com.intellij.diff.requests.DiffRequest
import com.intellij.diff.tools.combined.CombinedDiffModel.NewRequestData
import com.intellij.openapi.Disposable
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.CheckedDisposable
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresEdt
import java.util.*

interface CombinedDiffModel {
  val haveParentDisposable: Boolean
  val ourDisposable: CheckedDisposable
  val context: DiffContext
  val requests: Map<CombinedBlockId, DiffRequestProducer>

  fun init()
  fun reload()
  fun reset(requests: Map<CombinedBlockId, DiffRequestProducer>)

  fun add(requestData: NewRequestData, producer: DiffRequestProducer, onAdded: (CombinedBlockId) -> Unit = {})
  fun getCurrentRequest(): DiffRequest?

  fun getLoadedRequests(): List<DiffRequest>

  @RequiresBackgroundThread
  fun preloadRequests(indicator: ProgressIndicator, requests: List<RequestData>)
  fun loadRequestContents(blockIds: Collection<CombinedBlockId>, blockToSelect: CombinedBlockId?)
  fun unloadRequestContents(blockIds: Collection<CombinedBlockId>)

  fun addListener(listener: CombinedDiffModelListener, disposable: Disposable)

  data class NewRequestData(val blockId: CombinedBlockId, val position: InsertPosition)
  data class RequestData(val blockId: CombinedBlockId, val producer: DiffRequestProducer)
  data class InsertPosition(val blockId: CombinedBlockId, val above: Boolean)
}

interface CombinedDiffModelListener : EventListener {
  fun onModelReset()
  fun onContentReloadRequested(requests: Map<CombinedBlockId, DiffRequest>)

  @RequiresEdt
  fun onRequestAdded(requestData: NewRequestData, request: DiffRequest, onAdded: (CombinedBlockId) -> Unit)

  @RequiresEdt
  fun onRequestsPreloaded(requests: List<Pair<CombinedBlockId, DiffRequest>>)

  @RequiresEdt
  fun onRequestsLoaded(requests: Map<CombinedBlockId, DiffRequest>, blockIdToSelect: CombinedBlockId? = null)

  @RequiresEdt
  fun onRequestContentsUnloaded(requests: Map<CombinedBlockId, DiffRequest>)

  fun onProgressBar(visible: Boolean)
}