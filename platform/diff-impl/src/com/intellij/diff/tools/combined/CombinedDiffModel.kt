// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.tools.combined

import com.intellij.diff.DiffContext
import com.intellij.diff.requests.DiffRequest
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.CheckedDisposable
import com.intellij.util.concurrency.annotations.RequiresEdt
import java.util.*


interface CombinedDiffModel {
  val haveParentDisposable: Boolean
  val ourDisposable: CheckedDisposable
  val context: DiffContext
  val requests: List<CombinedBlockProducer>

  fun cleanBlocks()
  fun reload()

  /**
   * Updates current model with the new requests
   */
  fun setBlocks(requests: List<CombinedBlockProducer>)

  fun getLoadedRequest(blockId: CombinedBlockId): DiffRequest?
  fun getLoadedRequests(): List<DiffRequest>

  fun loadRequestContents(blockIds: Collection<CombinedBlockId>)
  fun unloadRequestContents(blockIds: Collection<CombinedBlockId>)

  fun addListener(listener: CombinedDiffModelListener, disposable: Disposable)
}

interface CombinedDiffModelListener : EventListener {
  fun onModelReset()

  @RequiresEdt
  fun onRequestsLoaded(blockId: CombinedBlockId, request: DiffRequest)

  @RequiresEdt
  fun onRequestContentsUnloaded(requests: Map<CombinedBlockId, DiffRequest>)
}