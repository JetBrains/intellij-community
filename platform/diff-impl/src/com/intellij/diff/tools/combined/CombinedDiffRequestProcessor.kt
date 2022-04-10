// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.tools.combined

import com.intellij.diff.*
import com.intellij.diff.FrameDiffTool.DiffViewer
import com.intellij.diff.chains.DiffRequestProducer
import com.intellij.diff.impl.DiffRequestProcessor
import com.intellij.diff.impl.DiffSettingsHolder
import com.intellij.diff.impl.DiffSettingsHolder.DiffSettings.Companion.getSettings
import com.intellij.diff.requests.DiffRequest
import com.intellij.diff.tools.combined.CombinedDiffRequest.NewChildDiffRequestData
import com.intellij.diff.tools.fragmented.UnifiedDiffTool
import com.intellij.diff.util.DiffUserDataKeys
import com.intellij.diff.util.DiffUserDataKeysEx
import com.intellij.diff.util.DiffUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.*
import com.intellij.openapi.progress.util.BackgroundTaskUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.UserDataHolder
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.util.Alarm
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.update.ComparableObject
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.Update
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.Dimension
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.min

private val LOG = logger<CombinedDiffRequestProcessor>()

interface CombinedDiffRequestProducer : DiffRequestProducer {
  fun getFilesSize(): Int
}

open class CombinedDiffRequestProcessor(private val project: Project?,
                                        private val requestProducer: CombinedDiffRequestProducer) {

  internal val ourDisposable = Disposer.newCheckedDisposable()

  @Suppress("LeakingThis")
  internal val context: DiffContext = CombinedDiffContext(DiffUtil.createUserDataHolder(COMBINED_DIFF_PROCESSOR, this))

  private val pendingUpdatesCount = AtomicInteger()

  private val contentLoadingQueue =
    MergingUpdateQueue("CombinedDiffRequestProcessor", 200, true, null, ourDisposable, null, Alarm.ThreadToUse.POOLED_THREAD)

  //
  // Diff context
  //

  private inner class CombinedDiffContext(private val myInitialContext: UserDataHolder) : DiffContextEx() {
    private val myOwnContext: UserDataHolder = UserDataHolderBase()

    override fun reopenDiffRequest() {}
    override fun reloadDiffRequest() {}
    override fun setWindowTitle(title: String) {}

    override fun showProgressBar(enabled: Boolean) {
      val ui = mainUi
      if (enabled) {
        ui.startProgress()
      }
      else {
        ui.stopProgress()
      }
    }

    override fun getProject() = this@CombinedDiffRequestProcessor.project
    override fun isFocusedInWindow(): Boolean = mainUi.isFocusedInWindow()
    override fun isWindowFocused(): Boolean = mainUi.isWindowFocused()
    override fun requestFocusInWindow() = mainUi.requestFocusInWindow()

    override fun <T> getUserData(key: Key<T>): T? {
      val data = myOwnContext.getUserData(key)
      return data ?: myInitialContext.getUserData(key)
    }

    override fun <T> putUserData(key: Key<T>, value: T?) {
      myOwnContext.putUserData(key, value)
    }
  }

  private val mainUi = CombinedDiffMainUI(context as DiffContextEx).also { Disposer.register(ourDisposable, it) }

  internal val request =
    EmptyProgressIndicator().let { indicator ->
      runBlockingCancellable(indicator) {
        withContext(Dispatchers.IO) {
          runUnderIndicator {
            requestProducer.process(context, indicator) as CombinedDiffRequest
          }
        }
      }
    }

  var viewer: CombinedDiffViewer? = buildUI()

  fun buildUI(): CombinedDiffViewer {
    val oldViewer = viewer
    if (oldViewer != null) {
      Disposer.dispose(oldViewer)
    }

    val newViewer = CombinedDiffViewer(context, mainUi.isUnified()).also { viewer ->
      Disposer.register(ourDisposable, viewer)
      mainUi.setContent(viewer)
      installBlockListener(viewer)
      buildCombinedDiffChildViewers(viewer, this@CombinedDiffRequestProcessor, request)
      notifyDiffViewerCreated(viewer, context, request)
    }

    viewer = newViewer

    return newViewer
  }

  private val blockCount get() = viewer?.diffBlocks?.size ?: requestProducer.getFilesSize()

  internal val filesSize get() = requestProducer.getFilesSize()

  internal val preferedFocusedComponent get() = mainUi.getPreferredFocusedComponent()

  internal val component get() = mainUi.getComponent()

  //
  // Navigation
  //

  internal enum class IterationState {
    NEXT, PREV, NONE
  }

  internal var iterationState = IterationState.NONE

  /**
   * @see com.intellij.openapi.vcs.changes.actions.diff.PresentableGoToChangePopupAction
   */
  open fun createGoToChangeAction(): AnAction? {
    return null
  }

  fun isNavigationEnabled(): Boolean = blockCount > 0

  fun hasNextChange(fromUpdate: Boolean): Boolean {
    val curFilesIndex = viewer?.scrollSupport?.blockIterable?.index ?: -1
    return curFilesIndex != -1 && curFilesIndex < blockCount - 1
  }

  fun hasPrevChange(fromUpdate: Boolean): Boolean {
    val curFilesIndex = viewer?.scrollSupport?.blockIterable?.index ?: -1
    return curFilesIndex != -1 && curFilesIndex > 0
  }

  fun goToNextChange(fromDifferences: Boolean) {
    goToChange(fromDifferences, true)
  }

  fun goToPrevChange(fromDifferences: Boolean) {
    goToChange(fromDifferences, false)
  }

  internal fun notifyGoDifferenceMessage(e: AnActionEvent, next: Boolean) {
    mainUi.notifyMessage(e, next)
  }

  private fun goToChange(fromDifferences: Boolean, next: Boolean) {
    val combinedDiffViewer = viewer ?: return
    val differencesIterable = combinedDiffViewer.getDifferencesIterable()
    val blocksIterable = combinedDiffViewer.getBlocksIterable()
    val canGoToDifference = { if (next) differencesIterable?.canGoNext() == true else differencesIterable?.canGoPrev() == true }
    val goToDifference = { if (next) differencesIterable?.goNext() else differencesIterable?.goPrev() }
    val canGoToBlock = { if (next) blocksIterable.canGoNext() else blocksIterable.canGoPrev() }
    val goToBlock = { if (next) blocksIterable.goNext() else blocksIterable.goPrev() }

    when {
      fromDifferences && canGoToDifference() -> goToDifference()
      fromDifferences && canGoToBlock() -> {
        goToBlock()
        combinedDiffViewer.selectDiffBlock(ScrollPolicy.DIFF_CHANGE)
      }

      canGoToBlock() -> {
        goToBlock()
        combinedDiffViewer.selectDiffBlock(ScrollPolicy.DIFF_BLOCK)
      }
    }
  }

  //
  // Lazy loading logic
  //

  private fun installBlockListener(viewer: CombinedDiffViewer) {
    viewer.addBlockListener(MyBlockListener())
  }

  private inner class MyBlockListener : BlockListener {

    override fun blocksHidden(blocks: Collection<CombinedDiffBlock<*>>) {
      val combinedViewer = viewer ?: return
      val combinedRequest = request

      for (block in blocks) {
        val blockId = block.id
        val childViewer = combinedViewer.diffViewers[blockId]
        if (childViewer is CombinedLazyDiffViewer) continue
        val index = combinedViewer.diffBlocksPositions[blockId] ?: continue
        val childRequest = combinedRequest.getChildRequest(index) ?: continue

        val loadingBlockContent = buildLoadingBlockContent(childRequest.producer, blockId, childViewer?.component?.size)
        combinedViewer.updateBlockContent(block, loadingBlockContent)
      }
    }

    override fun blocksVisible(blocks: Collection<CombinedDiffBlock<*>>, blockToSelect: CombinedDiffBlock<*>?) {
      val combinedViewer = viewer ?: return
      val blocksWithoutContent = blocks.filter { combinedViewer.diffViewers[it.id] is CombinedLazyDiffViewer }
      if (blocksWithoutContent.isNotEmpty()) {
        contentLoadingQueue.queue(LoadBlockContentRequest(blocksWithoutContent.map(CombinedDiffBlock<*>::id), blockToSelect))
      }
    }
  }

  private inner class LoadBlockContentRequest(private val blockIds: Collection<CombinedBlockId>,
                                              private val blockToSelect: CombinedDiffBlock<*>?) :
    Update(ComparableObject.Impl(*blockIds.toTypedArray()), pendingUpdatesCount.incrementAndGet()) {

    val indicator = EmptyProgressIndicator()

    override fun run() {
      loadVisibleContent(indicator, blockIds, blockToSelect)
      pendingUpdatesCount.decrementAndGet()
    }

    override fun canEat(update: Update?): Boolean = update is LoadBlockContentRequest && priority >= update.priority

    override fun setRejected() {
      super.setRejected()
      pendingUpdatesCount.decrementAndGet()
      indicator.cancel()
    }
  }

  internal fun loadVisibleContent(indicator: ProgressIndicator,
                                  visibleBlockIds: Collection<CombinedBlockId>,
                                  blockToSelect: CombinedDiffBlock<*>?) {
    val combinedViewer = viewer ?: return

    runInEdt { showProgressBar(true) }

    BackgroundTaskUtil.runUnderDisposeAwareIndicator(ourDisposable, {
      for (blockId in visibleBlockIds) {
        ProgressManager.checkCanceled()

        val lazyDiffViewer = combinedViewer.diffViewers[blockId] as? CombinedLazyDiffViewer ?: continue
        val childDiffRequest = lazyDiffViewer.requestProducer.process(context, indicator)

        childDiffRequest.putUserData(DiffUserDataKeysEx.EDITORS_HIDE_TITLE, true)

        runInEdt {
          buildBlockContent(combinedViewer, context, childDiffRequest, blockId)?.let { newContent ->
            combinedViewer.diffBlocks[blockId]?.let { block ->
              mainUi.countDifferences(blockId, newContent.viewer)
              combinedViewer.updateBlockContent(block, newContent)
              childDiffRequest.onAssigned(true)
            }
          }
        }
      }
    }, indicator)

    runInEdt {
      showProgressBar(false)
      combinedViewer.contentChanged()
      if (blockToSelect != null) {
        combinedViewer.selectDiffBlock(blockToSelect, ScrollPolicy.DIFF_BLOCK)
      }
    }
  }

  internal fun showProgressBar(enabled: Boolean) {
    (context as? DiffContextEx)?.showProgressBar(enabled)
  }

  //
  // Combined diff builder logic
  //

  @RequiresEdt
  fun addChildRequest(requestData: NewChildDiffRequestData, childRequestProducer: DiffRequestProducer): CombinedDiffBlock<*>? {
    val combinedViewer = viewer ?: return null
    val combinedRequest = request
    val indicator = EmptyProgressIndicator()
    val childDiffRequest =
      runBlockingCancellable(indicator) {
        withContext(Dispatchers.IO) { runUnderIndicator { childRequestProducer.process(context, indicator) } }
      }

    val position = requestData.position
    val childRequest = CombinedDiffRequest.ChildDiffRequest(childRequestProducer, requestData.blockId)

    combinedRequest.addChild(childRequest, position)

    return addNewChildDiffViewer(combinedViewer, context, requestData, childDiffRequest)
      ?.apply { Disposer.register(this, Disposable { combinedRequest.removeChild(childRequest) }) }
  }

  companion object {
    fun addNewChildDiffViewer(viewer: CombinedDiffViewer,
                              context: DiffContext,
                              diffRequestData: NewChildDiffRequestData,
                              request: DiffRequest,
                              needTakeTool: (FrameDiffTool) -> Boolean = { true }): CombinedDiffBlock<*>? {
      val content = buildBlockContent(viewer, context, request, diffRequestData.blockId, needTakeTool)
                    ?: return null
      return viewer.insertChildBlock(content, diffRequestData.position)
    }

    fun buildCombinedDiffChildViewers(viewer: CombinedDiffViewer, processor: CombinedDiffRequestProcessor, request: CombinedDiffRequest) {
      Alarm(viewer.component, viewer).addComponentRequest(
        Runnable {
          val childCount = request.getChildRequestsSize()
          val visibleBlockCount = min(viewer.scrollPane.visibleRect.height / CombinedLazyDiffViewer.HEIGHT.get(), childCount)
          val blocksOutsideViewportCount = childCount - visibleBlockCount
          val buildVisibleBlockIds = buildBlocks(viewer, request, to = visibleBlockCount)
          val indicator = EmptyProgressIndicator()

          BackgroundTaskUtil.executeOnPooledThread(viewer) {
            BackgroundTaskUtil.runUnderDisposeAwareIndicator(viewer, {
              if (buildVisibleBlockIds.isNotEmpty()) {
                processor.loadVisibleContent(indicator, buildVisibleBlockIds, null)
              }
              if (blocksOutsideViewportCount > 0) {

                runInEdt { processor.showProgressBar(true) }

                buildBlocks(viewer, request, from = visibleBlockCount)

                runInEdt { processor.showProgressBar(false) }

              }
            }, indicator)
          }
        }, 100
      )
    }

    private fun buildBlocks(viewer: CombinedDiffViewer,
                            request: CombinedDiffRequest,
                            from: Int = 0,
                            to: Int = request.getChildRequestsSize()): List<CombinedBlockId> {
      val childRequests = request.getChildRequests()
      assert(from in childRequests.indices) { "$from should be in ${childRequests.indices}" }
      assert(to in 0..childRequests.size) { "$to should be in ${0..childRequests.size}" }

      val buildBlockIds = arrayListOf<CombinedBlockId>()

      for (index in from until to) {
        ProgressManager.checkCanceled()
        val childRequest = childRequests[index]
        runInEdt {
          val content = buildLoadingBlockContent(childRequest.producer, childRequest.blockId)
          buildBlockIds.add(content.blockId)
          viewer.addChildBlock(content, index > 0)
        }
      }

      return buildBlockIds
    }

    internal fun buildBlockContent(viewer: CombinedDiffViewer,
                                   context: DiffContext,
                                   request: DiffRequest,
                                   blockId: CombinedBlockId,
                                   needTakeTool: (FrameDiffTool) -> Boolean = { true }): CombinedDiffBlockContent? {
      val diffSettings = getSettings(context.getUserData(DiffUserDataKeys.PLACE))
      val diffTools = DiffManagerEx.getInstance().diffTools
      request.putUserData(DiffUserDataKeys.ALIGNED_TWO_SIDED_DIFF, true)
      context.getUserData(DiffUserDataKeysEx.SCROLL_TO_CHANGE)?.let { request.putUserData(DiffUserDataKeysEx.SCROLL_TO_CHANGE, it) }

      val frameDiffTool =
        if (viewer.unifiedDiff && UnifiedDiffTool.INSTANCE.canShow(context, request)) {
          UnifiedDiffTool.INSTANCE
        }
        else {
          getDiffToolsExceptUnified(context, diffSettings, diffTools, request, needTakeTool)
        }

      val childViewer = frameDiffTool
                          ?.let { findSubstitutor(it, context, request) }
                          ?.createComponent(context, request)
                        ?: return null

      notifyDiffViewerCreated(childViewer, context, request)

      return CombinedDiffBlockContent(childViewer, blockId)
    }

    internal fun buildLoadingBlockContent(producer: DiffRequestProducer,
                                          blockId: CombinedBlockId,
                                          size: Dimension? = null): CombinedDiffBlockContent {
      return CombinedDiffBlockContent(CombinedLazyDiffViewer(producer, size), blockId)
    }

    private fun findSubstitutor(tool: FrameDiffTool, context: DiffContext, request: DiffRequest): FrameDiffTool {
      return DiffUtil.findToolSubstitutor(tool, context, request) as? FrameDiffTool ?: tool
    }

    private fun getDiffToolsExceptUnified(context: DiffContext,
                                          diffSettings: DiffSettingsHolder.DiffSettings,
                                          diffTools: List<DiffTool>,
                                          request: DiffRequest,
                                          needTakeTool: (FrameDiffTool) -> Boolean): FrameDiffTool? {

      return DiffRequestProcessor.getToolOrderFromSettings(diffSettings, diffTools)
        .asSequence().filterIsInstance<FrameDiffTool>()
        .filter { needTakeTool(it) && it !is UnifiedDiffTool && it.canShow(context, request) }
        .toList().let(DiffUtil::filterSuppressedTools).firstOrNull()
    }

    private fun notifyDiffViewerCreated(viewer: DiffViewer, context: DiffContext, request: DiffRequest) {
      DiffExtension.EP_NAME.forEachExtensionSafe { extension ->
        try {
          extension.onViewerCreated(viewer, context, request)
        }
        catch (e: Throwable) {
          LOG.error(e)
        }
      }
    }
  }

}
