// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.tools.combined

import com.intellij.diff.DiffContext
import com.intellij.diff.chains.DiffRequestProducer
import com.intellij.diff.requests.DiffRequest
import com.intellij.diff.util.DiffUserDataKeysEx
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.util.BackgroundTaskUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.UserDataHolder
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.util.Alarm
import com.intellij.util.EventDispatcher
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.ui.update.ComparableObject
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.Update
import java.util.concurrent.atomic.AtomicInteger

class CombinedDiffModelImpl(val project: Project,
                            override val haveParentDisposable: Boolean = false) : CombinedDiffModel {
  override val ourDisposable = Disposer.newCheckedDisposable()

  private val modelListeners = EventDispatcher.create(CombinedDiffModelListener::class.java)

  private val contentLoadingQueue =
    MergingUpdateQueue("CombinedDiffModel", 0, true, null, ourDisposable, null, Alarm.ThreadToUse.POOLED_THREAD)

  private val pendingUpdatesCount = AtomicInteger()

  private var _requests: Map<CombinedBlockId, CombinedBlockProducer> = emptyMap()

  private val loadedRequests = mutableMapOf<CombinedBlockId, DiffRequest>()

  override val requests: List<CombinedBlockProducer> get() = _requests.values.toList()

  override val context: DiffContext = CombinedDiffContext(project)

  override fun cleanBlocks() {
    cleanLoadedRequests()
  }

  private fun cleanLoadedRequests() {
    loadedRequests.forEach { it.value.onAssigned(false) }
    loadedRequests.clear()
  }

  override fun reload() {
    val previouslyLoaded = loadedRequests.toMap()
    cleanLoadedRequests()
    if (previouslyLoaded.isNotEmpty()) {
      loadRequestContents(previouslyLoaded.keys)
    }
  }

  override fun setBlocks(requests: List<CombinedBlockProducer>) {
    cleanLoadedRequests()
    _requests = requests.associateBy { it.id }
    modelListeners.multicaster.onModelReset()
  }

  override fun getLoadedRequest(blockId: CombinedBlockId): DiffRequest? {
    return loadedRequests[blockId]
  }

  override fun getLoadedRequests(): List<DiffRequest> = loadedRequests.values.toList()

  override fun loadRequestContents(blockIds: Collection<CombinedBlockId>) {
    val notLoadedBlockIds = blockIds.filter { it !in loadedRequests }
    if (notLoadedBlockIds.isNotEmpty()) {
      contentLoadingQueue.queue(LoadContentRequest(notLoadedBlockIds))
    }
  }

  override fun unloadRequestContents(blockIds: Collection<CombinedBlockId>) {
    val unloadedRequests = mutableMapOf<CombinedBlockId, DiffRequest>()
    val loadedRequestsLimit = CombinedDiffRegistry.getMaxBlockCountInMemory()

    for (blockId in blockIds) {
      if (loadedRequestsLimit < 0 || loadedRequestsLimit < loadedRequests.size) {
        val unloadedRequest = loadedRequests.remove(blockId) ?: continue
        unloadedRequests[blockId] = unloadedRequest
      }
    }
    if (unloadedRequests.isNotEmpty()) {
      modelListeners.multicaster.onRequestContentsUnloaded(unloadedRequests)
    }
  }

  override fun addListener(listener: CombinedDiffModelListener, disposable: Disposable) =
    modelListeners.addListener(listener, disposable)

  @RequiresBackgroundThread
  private fun loadRequests(indicator: ProgressIndicator, blockIds: Collection<CombinedBlockId>) {
    BackgroundTaskUtil.runUnderDisposeAwareIndicator(ourDisposable, {
      for (blockId in blockIds) {
        ProgressManager.checkCanceled()

        if (loadedRequests.contains(blockId)) {
          continue
        }

        val requestProducer = _requests[blockId] ?: continue
        val loadedRequest = loadRequest(indicator, blockId, requestProducer.producer)
        runInEdt { modelListeners.multicaster.onRequestsLoaded(blockId, loadedRequest) }
      }
    }, indicator)
  }

  @RequiresBackgroundThread
  private fun loadRequest(indicator: ProgressIndicator, blockId: CombinedBlockId, producer: DiffRequestProducer): DiffRequest {
    val request = producer.process(context, indicator)
    request.putUserData(DiffUserDataKeysEx.EDITORS_HIDE_TITLE, true)
    loadedRequests[blockId] = request
    return request
  }

  private inner class LoadContentRequest(private val blockIds: Collection<CombinedBlockId>) :
    Update(ComparableObject.Impl(*blockIds.toTypedArray()), pendingUpdatesCount.incrementAndGet()) {

    val indicator = EmptyProgressIndicator()

    override fun run() {
      loadRequests(indicator, blockIds)
      pendingUpdatesCount.decrementAndGet()
    }

    override fun canEat(update: Update): Boolean = update is LoadContentRequest && priority >= update.priority

    override fun setRejected() {
      super.setRejected()
      pendingUpdatesCount.decrementAndGet()
      indicator.cancel()
    }
  }

  private inner class CombinedDiffContext(private val initialContext: UserDataHolder) : DiffContext() {
    private val mainUi get() = getUserData(COMBINED_DIFF_MAIN_UI)

    private val ownContext: UserDataHolder = UserDataHolderBase()

    init {
      ownContext.putUserData(COMBINED_DIFF_MODEL, this@CombinedDiffModelImpl)
    }

    override fun getProject() = this@CombinedDiffModelImpl.project
    override fun isFocusedInWindow(): Boolean = mainUi?.isFocusedInWindow() ?: false
    override fun isWindowFocused(): Boolean = mainUi?.isWindowFocused() ?: false
    override fun requestFocusInWindow() {
      mainUi?.requestFocusInWindow()
    }

    override fun <T> getUserData(key: Key<T>): T? {
      val data = ownContext.getUserData(key)
      return data ?: initialContext.getUserData(key)
    }

    override fun <T> putUserData(key: Key<T>, value: T?) {
      ownContext.putUserData(key, value)
    }
  }
}