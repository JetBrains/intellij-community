// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.tools.combined

import com.intellij.diff.*
import com.intellij.diff.impl.DiffRequestProcessor
import com.intellij.diff.impl.DiffSettingsHolder
import com.intellij.diff.requests.DiffRequest
import com.intellij.diff.tools.fragmented.UnifiedDiffTool
import com.intellij.diff.util.DiffUserDataKeys
import com.intellij.diff.util.DiffUserDataKeysEx
import com.intellij.diff.util.DiffUtil
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.Disposer
import com.intellij.util.concurrency.annotations.RequiresEdt
import java.awt.Dimension

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

    combinedViewer = createCombinedViewer(true)
  }

  internal fun getPreferredFocusedComponent() = mainUi.getPreferredFocusedComponent()

  internal fun getMainComponent() = mainUi.getComponent()

  private fun createMainUI(): CombinedDiffMainUI {
    return CombinedDiffMainUI(model, ::createGoToChangeAction).also { ui ->
      Disposer.register(ourDisposable, ui)
      model.context.putUserData(COMBINED_DIFF_MAIN_UI, ui)
    }
  }

  private fun createCombinedViewer(initialFocusRequest: Boolean): CombinedDiffViewer {
    val context = model.context
    val blocks = model.requests.keys.toList()
    val blockToSelect = model.context.getUserData(COMBINED_DIFF_SCROLL_TO_BLOCK)

    return CombinedDiffViewer(context, blocks, blockToSelect, MyBlockListener()).also { viewer ->
      Disposer.register(ourDisposable, viewer)
      context.putUserData(COMBINED_DIFF_VIEWER_KEY, viewer)
      context.putUserData(COMBINED_DIFF_VIEWER_INITIAL_FOCUS_REQUEST, initialFocusRequest)
      mainUi.setContent(viewer)
    }
  }

  protected abstract fun createGoToChangeAction(): AnAction?

  private inner class ModelListener : CombinedDiffModelListener {
    override fun onModelReset() {
      Disposer.dispose(combinedViewer)
      model.context.putUserData(COMBINED_DIFF_VIEWER_KEY, null)

      combinedViewer = createCombinedViewer(false)
    }

    @RequiresEdt
    override fun onRequestsLoaded(blockId: CombinedBlockId, request: DiffRequest) {
      buildBlockContent(mainUi, model.context, request, blockId)?.let { newContent ->
        mainUi.countDifferences(blockId, newContent.viewer)
        combinedViewer.updateBlockContent(newContent)
        request.onAssigned(true)
      }

      combinedViewer.contentChanged()
    }

    @RequiresEdt
    override fun onRequestContentsUnloaded(requests: Map<CombinedBlockId, DiffRequest>) {
      for ((blockId, request) in requests) {
        combinedViewer.replaceBlockWithPlaceholder(blockId)
        request.onAssigned(false)
      }
    }
  }

  private inner class MyBlockListener : BlockListener {

    override fun blocksHidden(blockIds: Collection<CombinedBlockId>) {
      model.unloadRequestContents(blockIds)
    }

    override fun blocksVisible(blockIds: Collection<CombinedBlockId>) {
      model.loadRequestContents(blockIds)
    }
  }

  companion object {
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
      return CombinedDiffBlockContent(CombinedDiffLoadingBlock(size), blockId)
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
