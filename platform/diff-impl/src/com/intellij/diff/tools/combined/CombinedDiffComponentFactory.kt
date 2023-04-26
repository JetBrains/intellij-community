// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.tools.combined

import com.intellij.diff.*
import com.intellij.diff.impl.DiffRequestProcessor
import com.intellij.diff.impl.DiffSettingsHolder
import com.intellij.diff.requests.DiffRequest
import com.intellij.diff.tools.combined.CombinedDiffModel.NewRequestData
import com.intellij.diff.tools.fragmented.UnifiedDiffTool
import com.intellij.diff.util.DiffUserDataKeys
import com.intellij.diff.util.DiffUserDataKeysEx
import com.intellij.diff.util.DiffUtil
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.util.BackgroundTaskUtil
import com.intellij.openapi.util.Disposer
import com.intellij.util.Alarm
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.EdtInvocationManager.invokeAndWaitIfNeeded
import java.awt.Dimension
import kotlin.math.min

private val LOG = logger<CombinedDiffComponentFactory>()

interface CombinedDiffComponentFactoryProvider {
  fun create(model: CombinedDiffModel): CombinedDiffComponentFactory
}

abstract class CombinedDiffComponentFactory(val model: CombinedDiffModel) {

  internal val ourDisposable = Disposer.newCheckedDisposable()

  private val mainUi: CombinedDiffMainUI

  private var combinedViewer: CombinedDiffViewer

  init {
    if (model.haveParentDisposable) { // diff preview scenario?
      Disposer.register(model.ourDisposable, ourDisposable)
    }
    else { // diff from action
      Disposer.register(ourDisposable, model.ourDisposable)
    }
    model.addListener(ModelListener(), ourDisposable)
    mainUi = createMainUI()
    combinedViewer = createCombinedViewer()

    buildCombinedDiffChildBlocks()
  }

  internal fun getPreferredFocusedComponent() = mainUi.getPreferredFocusedComponent()

  internal fun getMainComponent() = mainUi.getComponent()

  private fun createMainUI(): CombinedDiffMainUI {
    return CombinedDiffMainUI(model, ::createGoToChangeAction).also { ui ->
      Disposer.register(ourDisposable, ui)
      model.context.putUserData(COMBINED_DIFF_MAIN_UI, ui)
    }
  }

  private fun createCombinedViewer(): CombinedDiffViewer {
    val context = model.context
    return CombinedDiffViewer(context).also { viewer ->
      Disposer.register(ourDisposable, viewer)
      context.putUserData(COMBINED_DIFF_VIEWER_KEY, viewer)
      viewer.addBlockListener(MyBlockListener())
      mainUi.setContent(viewer)
    }
  }

  protected abstract fun createGoToChangeAction(): AnAction?

  private inner class ModelListener : CombinedDiffModelListener {
    override fun onProgressBar(visible: Boolean) {
      runInEdt { if (visible) mainUi.startProgress() else mainUi.stopProgress() }
    }

    override fun onModelReset() {
      Disposer.dispose(combinedViewer)
      combinedViewer = createCombinedViewer()
      buildCombinedDiffChildBlocks()
    }

    @RequiresEdt
    override fun onRequestsLoaded(requests: Map<CombinedBlockId, DiffRequest>, blockIdToSelect: CombinedBlockId?) {
      for ((blockId, request) in requests) {
        buildBlockContent(mainUi, model.context, request, blockId)?.let { newContent ->
          combinedViewer.getBlockForId(blockId)?.let { block ->
            mainUi.countDifferences(blockId, newContent.viewer)
            combinedViewer.updateBlockContent(block, newContent)
            request.onAssigned(true)
          }
        }
      }

      combinedViewer.contentChanged()

      if (blockIdToSelect != null) {
        combinedViewer.getBlockForId(blockIdToSelect)?.let { block ->
          combinedViewer.selectDiffBlock(block, ScrollPolicy.DIFF_BLOCK, false)
        }
      }
    }

    @RequiresEdt
    override fun onRequestContentsUnloaded(requests: Map<CombinedBlockId, DiffRequest>) {
      for ((blockId, request) in requests) {
        val size = combinedViewer.getDiffViewerForId(blockId)?.component?.size
        buildLoadingBlockContent(blockId, size).let { newContent ->
          combinedViewer.getBlockForId(blockId)?.let { block ->
            combinedViewer.updateBlockContent(block, newContent)
            request.onAssigned(false)
          }
        }
      }
    }

    @RequiresEdt
    override fun onRequestAdded(requestData: NewRequestData, request: DiffRequest, onAdded: (CombinedBlockId) -> Unit) {
      addNewBlock(mainUi, combinedViewer, model.context, requestData, request)?.run { onAdded(id) }
    }
  }

  private inner class MyBlockListener : BlockListener {

    override fun blocksHidden(blocks: Collection<CombinedDiffBlock<*>>) {
      val blockIds = blocks.asSequence().map(CombinedDiffBlock<*>::id).toSet()
      model.unloadRequestContents(blockIds)
    }

    override fun blocksVisible(blocks: Collection<CombinedDiffBlock<*>>, blockToSelect: CombinedBlockId?) {
      val blockIds = blocks.asSequence().map(CombinedDiffBlock<*>::id).toSet()
      model.loadRequestContents(blockIds, blockToSelect)
    }
  }

  private fun buildCombinedDiffChildBlocks() {
    Alarm(combinedViewer.component, combinedViewer).addComponentRequest(
      Runnable {
        val childCount = model.requests.size
        val visibleBlockCount = min(combinedViewer.scrollPane.visibleRect.height / CombinedLazyDiffViewer.HEIGHT.get(), childCount)
        val blockToSelect = model.context.getUserData(COMBINED_DIFF_SCROLL_TO_BLOCK)

        BackgroundTaskUtil.executeOnPooledThread(ourDisposable) {
         val indicator = EmptyProgressIndicator()
          BackgroundTaskUtil.runUnderDisposeAwareIndicator(ourDisposable, {

            runInEdt { mainUi.startProgress() }
            try {
              val allRequests = model.requests
                .asSequence()
                .map { CombinedDiffModel.RequestData(it.key, it.value) }

              allRequests.forEachIndexed { index, childRequest ->
                invokeAndWaitIfNeeded {
                  combinedViewer.addChildBlock(buildLoadingBlockContent(childRequest.blockId), index > 0 || visibleBlockCount > 0)
                }
              }
            }
            finally {
              runInEdt {
                mainUi.stopProgress()
                if (blockToSelect != null) {
                  combinedViewer.selectDiffBlock(blockToSelect, ScrollPolicy.DIFF_BLOCK, true)
                }
              }
            }

          }, indicator)
        }
      }, 100
    )
  }

  companion object {
    private fun addNewBlock(mainUi: CombinedDiffMainUI,
                            viewer: CombinedDiffViewer,
                            context: DiffContext,
                            requestData: NewRequestData,
                            request: DiffRequest,
                            needTakeTool: (FrameDiffTool) -> Boolean = { true }): CombinedDiffBlock<*>? {
      val content = buildBlockContent(mainUi, context, request, requestData.blockId, needTakeTool)
                    ?: return null
      return viewer.insertChildBlock(content, requestData.position)
    }

    private fun buildBlockContent(mainUi: CombinedDiffMainUI,
                                  context: DiffContext,
                                  request: DiffRequest,
                                  blockId: CombinedBlockId,
                                  needTakeTool: (FrameDiffTool) -> Boolean = { true }): CombinedDiffBlockContent? {
      val diffSettings = DiffSettingsHolder.DiffSettings.getSettings(context.getUserData(DiffUserDataKeys.PLACE))
      val diffTools = DiffManagerEx.getInstance().diffTools
      request.putUserData(DiffUserDataKeys.ALIGNED_TWO_SIDED_DIFF, true)
      context.getUserData(DiffUserDataKeysEx.SCROLL_TO_CHANGE)?.let { request.putUserData(DiffUserDataKeysEx.SCROLL_TO_CHANGE, it) }

      val frameDiffTool =
        if (mainUi.isUnified() && UnifiedDiffTool.INSTANCE.canShow(context, request)) {
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

    private fun buildLoadingBlockContent(blockId: CombinedBlockId, size: Dimension? = null): CombinedDiffBlockContent {
      return CombinedDiffBlockContent(CombinedLazyDiffViewer(size), blockId)
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

    private fun notifyDiffViewerCreated(viewer: FrameDiffTool.DiffViewer, context: DiffContext, request: DiffRequest) {
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
