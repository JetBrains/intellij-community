// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.tools.combined

import com.intellij.diff.DiffContext
import com.intellij.diff.chains.DiffRequestProducer
import com.intellij.diff.requests.DiffRequest
import com.intellij.diff.tools.combined.CombinedDiffModel.NewRequestData
import com.intellij.diff.tools.combined.CombinedDiffModel.RequestData
import com.intellij.diff.util.DiffUserDataKeysEx
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.progress.*
import com.intellij.openapi.progress.util.BackgroundTaskUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.*
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.Alarm
import com.intellij.util.EventDispatcher
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.update.ComparableObject
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.Update
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.Pair

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

open class CombinedDiffModelImpl(protected val project: Project,
                                 requests: Map<CombinedBlockId, DiffRequestProducer>,
                                 parentDisposable: Disposable? = null) : CombinedDiffModel {

  override val haveParentDisposable = parentDisposable != null

  override val ourDisposable = Disposer.newCheckedDisposable().also {
    if (parentDisposable != null) Disposer.register(parentDisposable, it)
  }

  private val modelListeners = EventDispatcher.create(CombinedDiffModelListener::class.java)

  private val contentLoadingQueue =
    MergingUpdateQueue("CombinedDiffModel", 200, true, null, ourDisposable, null, Alarm.ThreadToUse.POOLED_THREAD)

  private val pendingUpdatesCount = AtomicInteger()

  private var _requests = requests.toMutableMap()

  private val loadedRequests = mutableMapOf<CombinedBlockId, DiffRequest>()

  override val requests: Map<CombinedBlockId, DiffRequestProducer> get() = _requests

  override val context: DiffContext = CombinedDiffContext(project)

  override fun init() {
    loadedRequests.forEach { it.value.onAssigned(false) }
    loadedRequests.clear()
  }

  override fun reload() {
    val previouslyLoaded = loadedRequests.toMap()
    init()
    if (previouslyLoaded.isNotEmpty()) {
      modelListeners.multicaster.onContentReloadRequested(previouslyLoaded)
    }
  }

  override fun reset(requests: Map<CombinedBlockId, DiffRequestProducer>) {
    init()
    _requests = requests.toMutableMap()
    modelListeners.multicaster.onModelReset()
  }

  override fun add(requestData: NewRequestData, producer: DiffRequestProducer, onAdded: (CombinedBlockId) -> Unit) {
    val blockId = requestData.blockId
    _requests[blockId] = producer
    val indicator = EmptyProgressIndicator()
    BackgroundTaskUtil.runUnderDisposeAwareIndicator(ourDisposable, {
      modelListeners.multicaster.onProgressBar(true)

      val request = runBlockingCancellable(indicator) {
        withContext(Dispatchers.IO) { coroutineToIndicator { loadRequest(indicator, blockId, producer) } }
      }

      modelListeners.multicaster.onProgressBar(false)
      runInEdt { modelListeners.multicaster.onRequestAdded(requestData, request, onAdded) }
    }, indicator)
  }

  override fun getCurrentRequest(): DiffRequest? {
    return context.getUserData(COMBINED_DIFF_VIEWER_KEY)?.getCurrentBlockId()?.let(loadedRequests::get)
  }

  override fun getLoadedRequests(): List<DiffRequest> = loadedRequests.values.toList()

  @RequiresBackgroundThread
  override fun preloadRequests(indicator: ProgressIndicator, requests: List<RequestData>) {
    val preloadedRequests = requests.map { it.blockId to loadRequest(indicator, it.blockId, it.producer) }
    modelListeners.multicaster.onRequestsPreloaded(preloadedRequests)
  }

  override fun loadRequestContents(blockIds: Collection<CombinedBlockId>, blockToSelect: CombinedBlockId?) {
    val notLoadedBlockIds = blockIds.filter { it !in loadedRequests }
    if (notLoadedBlockIds.isNotEmpty()) {
      contentLoadingQueue.queue(LoadContentRequest(notLoadedBlockIds, blockToSelect))
    }
  }

  override fun unloadRequestContents(blockIds: Collection<CombinedBlockId>) {
    val unloadedRequests = mutableMapOf<CombinedBlockId, DiffRequest>()
    val loadedRequestsLimit = Registry.intValue("combined.diff.loaded.content.limit")

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
  private fun loadRequests(indicator: ProgressIndicator, blockIds: Collection<CombinedBlockId>, blockToSelect: CombinedBlockId? = null) {
    BackgroundTaskUtil.runUnderDisposeAwareIndicator(ourDisposable, {
      val loadedDiffRequests = hashMapOf<CombinedBlockId, DiffRequest>()
      modelListeners.multicaster.onProgressBar(true)
      for (blockId in blockIds) {
        ProgressManager.checkCanceled()

        if (loadedRequests.contains(blockId)) {
          continue
        }

        val requestProducer = requests[blockId] ?: continue
        loadedDiffRequests[blockId] = loadRequest(indicator, blockId, requestProducer)
      }

      modelListeners.multicaster.onProgressBar(false)
      runInEdt { modelListeners.multicaster.onRequestsLoaded(loadedDiffRequests, blockToSelect) }

    }, indicator)
  }

  @RequiresBackgroundThread
  private fun loadRequest(indicator: ProgressIndicator, blockId: CombinedBlockId, producer: DiffRequestProducer): DiffRequest {
    val request = producer.process(context, indicator)
    request.putUserData(DiffUserDataKeysEx.EDITORS_HIDE_TITLE, true)
    loadedRequests[blockId] = request
    return request
  }

  private inner class LoadContentRequest(private val blockIds: Collection<CombinedBlockId>,
                                         private val blockToSelect: CombinedBlockId?) :
    Update(ComparableObject.Impl(*blockIds.toTypedArray()), pendingUpdatesCount.incrementAndGet()) {

    val indicator = EmptyProgressIndicator()

    override fun run() {
      loadRequests(indicator, blockIds, blockToSelect)
      pendingUpdatesCount.decrementAndGet()
    }

    override fun canEat(update: Update?): Boolean = update is LoadContentRequest && priority >= update.priority

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
