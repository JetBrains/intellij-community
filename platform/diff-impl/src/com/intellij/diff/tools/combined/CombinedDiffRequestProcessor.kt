// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.tools.combined

import com.intellij.diff.*
import com.intellij.diff.chains.DiffRequestProducer
import com.intellij.diff.impl.CacheDiffRequestProcessor
import com.intellij.diff.impl.DiffRequestProcessor
import com.intellij.diff.impl.DiffSettingsHolder
import com.intellij.diff.impl.DiffSettingsHolder.DiffSettings.Companion.getSettings
import com.intellij.diff.impl.ui.DifferencesLabel
import com.intellij.diff.requests.DiffRequest
import com.intellij.diff.tools.combined.CombinedDiffRequest.NewChildDiffRequestData
import com.intellij.diff.tools.combined.CombinedDiffRequestProcessor.CombinedDiffViewerBuilder.Companion.buildLoadingBlockContent
import com.intellij.diff.tools.fragmented.UnifiedDiffTool
import com.intellij.diff.tools.util.PrevNextDifferenceIterable
import com.intellij.diff.tools.util.base.DiffViewerBase
import com.intellij.diff.tools.util.base.DiffViewerListener
import com.intellij.diff.util.DiffUserDataKeys
import com.intellij.diff.util.DiffUserDataKeysEx
import com.intellij.diff.util.DiffUserDataKeysEx.ScrollToPolicy
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

open class CombinedDiffRequestProcessor(project: Project?,
                                        private val requestProducer: CombinedDiffRequestProducer) :
  CacheDiffRequestProcessor.Simple(project, DiffUtil.createUserDataHolder(DiffUserDataKeysEx.DIFF_NEW_TOOLBAR, true)) {

  init {
    @Suppress("LeakingThis")
    context.putUserData(COMBINED_DIFF_PROCESSOR, this)
  }

  override fun getCurrentRequestProvider(): DiffRequestProducer = requestProducer

  protected val viewer get() = activeViewer as? CombinedDiffViewer
  protected val request get() = activeRequest as? CombinedDiffRequest

  private val blockCount get() = viewer?.diffBlocks?.size ?: requestProducer.getFilesSize()

  private val pendingUpdatesCount = AtomicInteger()

  @Suppress("LeakingThis")
  private val contentLoadingQueue =
    MergingUpdateQueue("CombinedDiffRequestProcessor", 200, true, null, this, null, Alarm.ThreadToUse.POOLED_THREAD)
      .also { Disposer.register(this, it) }

  //
  // Global, shortcuts only navigation actions
  //

  private val openInEditorAction = object : MyOpenInEditorAction() {
    override fun update(e: AnActionEvent) {
      super.update(e)
      e.presentation.isVisible = false
    }
  }

  private val prevFileAction = object : MyPrevChangeAction() {
    override fun update(e: AnActionEvent) {
      super.update(e)
      e.presentation.isVisible = false
    }
  }

  private val nextFileAction = object : MyNextChangeAction() {
    override fun update(e: AnActionEvent) {
      super.update(e)
      e.presentation.isVisible = false
    }
  }

  private val prevDifferenceAction = object : MyPrevDifferenceAction() {
    override fun getDifferenceIterable(e: AnActionEvent): PrevNextDifferenceIterable? {
      return viewer?.scrollSupport?.currentPrevNextIterable ?: super.getDifferenceIterable(e)
    }
  }

  private val nextDifferenceAction = object : MyNextDifferenceAction() {
    override fun getDifferenceIterable(e: AnActionEvent): PrevNextDifferenceIterable? {
      return viewer?.scrollSupport?.currentPrevNextIterable ?: super.getDifferenceIterable(e)
    }
  }

  private val differencesLabel by lazy { MyDifferencesLabel(createGoToChangeAction()) }

  //
  // Navigation
  //

  override fun getNavigationActions(): List<AnAction> {
    return listOfNotNull(prevDifferenceAction, nextDifferenceAction, differencesLabel,
                         openInEditorAction, prevFileAction, nextFileAction)
  }
  final override fun isNavigationEnabled(): Boolean = blockCount > 0

  final override fun hasNextChange(fromUpdate: Boolean): Boolean {
    val curFilesIndex = viewer?.scrollSupport?.blockIterable?.index ?: -1
    return curFilesIndex != -1 && curFilesIndex < blockCount - 1
  }

  final override fun hasPrevChange(fromUpdate: Boolean): Boolean {
    val curFilesIndex = viewer?.scrollSupport?.blockIterable?.index ?: -1
    return curFilesIndex != -1 && curFilesIndex > 0
  }

  final override fun goToNextChange(fromDifferences: Boolean) {
    goToChange(fromDifferences, true)
  }

  final override fun goToPrevChange(fromDifferences: Boolean) {
    goToChange(fromDifferences, false)
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
        context.putUserData(DiffUserDataKeysEx.SCROLL_TO_CHANGE,
                                        if (next) ScrollToPolicy.FIRST_CHANGE else ScrollToPolicy.LAST_CHANGE)
        combinedDiffViewer.selectDiffBlock(ScrollPolicy.DIFF_CHANGE)
      }

      canGoToBlock() -> {
        goToBlock()
        combinedDiffViewer.selectDiffBlock(ScrollPolicy.DIFF_BLOCK)
      }
    }
  }

  private inner class MyDifferencesLabel(goToChangeAction: AnAction?) :
    DifferencesLabel(goToChangeAction, myToolbarWrapper.targetComponent) {

    private val loadedDifferences = hashMapOf<Int, Int>()

    override fun getFileCount(): Int = requestProducer.getFilesSize()
    override fun getTotalDifferences(): Int = calculateTotalDifferences()

    fun countDifferences(blockId: CombinedBlockId, childViewer: FrameDiffTool.DiffViewer) {
      val combinedViewer = viewer ?: return
      val index = combinedViewer.diffBlocksPositions[blockId] ?: return

      loadedDifferences[index] = 1

      if (childViewer is DiffViewerBase) {
        val listener = object : DiffViewerListener() {
          override fun onAfterRediff() {
            loadedDifferences[index] = if (childViewer is DifferencesCounter) childViewer.getTotalDifferences() else 1
          }
        }
        childViewer.addListener(listener)
        Disposer.register(childViewer, Disposable { childViewer.removeListener(listener) })
      }
    }

    private fun calculateTotalDifferences(): Int = loadedDifferences.values.sum()
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
      val combinedRequest = request ?: return

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

    BackgroundTaskUtil.runUnderDisposeAwareIndicator(this, {
      for (blockId in visibleBlockIds) {
        ProgressManager.checkCanceled()

        val lazyDiffViewer = combinedViewer.diffViewers[blockId] as? CombinedLazyDiffViewer ?: continue
        val childDiffRequest =
          runBlockingCancellable(indicator) { runUnderIndicator { loadRequest(lazyDiffViewer.requestProducer, indicator) } }

        childDiffRequest.putUserData(DiffUserDataKeysEx.EDITORS_HIDE_TITLE, true)

        runInEdt {
          CombinedDiffViewerBuilder.buildBlockContent(combinedViewer, context, childDiffRequest, blockId)?.let { newContent ->
            combinedViewer.diffBlocks[blockId]?.let { block ->
              differencesLabel.countDifferences(blockId, newContent.viewer)
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
    val combinedRequest = request ?: return null
    val indicator = EmptyProgressIndicator()
    val childDiffRequest =
      runBlockingCancellable(indicator) {
        withContext(Dispatchers.IO) { runUnderIndicator { loadRequest(childRequestProducer, indicator) } }
      }

    val position = requestData.position
    val childRequest = CombinedDiffRequest.ChildDiffRequest(childRequestProducer, requestData.blockId)

    combinedRequest.addChild(childRequest, position)

    return CombinedDiffViewerBuilder.addNewChildDiffViewer(combinedViewer, context, requestData, childDiffRequest)
      ?.apply { Disposer.register(this, Disposable { combinedRequest.removeChild(childRequest) }) }
  }

  class CombinedDiffViewerBuilder : DiffExtension() {

    override fun onViewerCreated(viewer: FrameDiffTool.DiffViewer, context: DiffContext, request: DiffRequest) {

      if (request !is CombinedDiffRequest) return
      if (viewer !is CombinedDiffViewer) return

      context.getUserData(COMBINED_DIFF_PROCESSOR)?.let { processor ->
        processor.installBlockListener(viewer)
        buildCombinedDiffChildViewers(viewer, processor, request)
      }
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

        EP_NAME.forEachExtensionSafe { extension ->
          try {
            extension.onViewerCreated(childViewer, context, request)
          }
          catch (e: Throwable) {
            LOG.error(e)
          }
        }

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

    }
  }

}
