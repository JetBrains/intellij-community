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
import com.intellij.diff.tools.fragmented.UnifiedDiffTool
import com.intellij.diff.tools.util.PrevNextDifferenceIterable
import com.intellij.diff.util.DiffUserDataKeys
import com.intellij.diff.util.DiffUserDataKeysEx
import com.intellij.diff.util.DiffUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.progress.runUnderIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.FileStatus
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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

  //
  // Navigation
  //

  override fun getNavigationActions(): List<AnAction> {
    val goToChangeAction = createGoToChangeAction()
    return listOfNotNull(prevDifferenceAction, nextDifferenceAction, MyDifferencesLabel(goToChangeAction),
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

    override fun getFileCount(): Int = requestProducer.getFilesSize()
    override fun getTotalDifferences(): Int = calculateTotalDifferences()

    private fun calculateTotalDifferences(): Int {
      val combinedViewer = viewer ?: return 0

      return combinedViewer.diffBlocks
        .asSequence()
        .map { it.content.viewer}
        .sumOf { (it as? DifferencesCounter)?.getTotalDifferences() ?: 1 }
    }
  }

  //
  // Combined diff builder logic
  //

  @RequiresEdt
  fun addChildRequest(requestData: NewChildDiffRequestData, childRequestProducer: DiffRequestProducer): CombinedDiffBlock? {
    val combinedViewer = viewer ?: return null
    val combinedRequest = request ?: return null
    val indicator = EmptyProgressIndicator()
    val childDiffRequest =
      runBlockingCancellable(indicator) {
        withContext(Dispatchers.IO) { runUnderIndicator { loadRequest(childRequestProducer, indicator) } }
      }

    val position = requestData.position
    val childRequest =
      CombinedDiffRequest.ChildDiffRequest(childDiffRequest, requestData.path, requestData.fileStatus)

    combinedRequest.addChild(childRequest, position)

    return CombinedDiffViewerBuilder.addNewChildDiffViewer(combinedViewer, context, requestData, childDiffRequest)
      ?.apply { Disposer.register(this, Disposable { combinedRequest.removeChild(childRequest) }) }
  }

  class CombinedDiffViewerBuilder : DiffExtension() {

    override fun onViewerCreated(viewer: FrameDiffTool.DiffViewer, context: DiffContext, request: DiffRequest) {

      if (request !is CombinedDiffRequest) return
      if (viewer !is CombinedDiffViewer) return

      buildCombinedDiffChildViewers(viewer, context, request)
    }

    companion object {
      fun addNewChildDiffViewer(viewer: CombinedDiffViewer,
                                context: DiffContext,
                                diffRequestData: NewChildDiffRequestData,
                                request: DiffRequest,
                                needTakeTool: (FrameDiffTool) -> Boolean = { true }): CombinedDiffBlock? {
        val content = buildBlockContent(viewer, context, request, diffRequestData.path, diffRequestData.fileStatus, needTakeTool)
                      ?: return null
        return viewer.insertChildBlock(content, diffRequestData.position)
      }

      fun buildCombinedDiffChildViewers(viewer: CombinedDiffViewer,
                                        context: DiffContext,
                                        request: CombinedDiffRequest,
                                        needTakeTool: (FrameDiffTool) -> Boolean = { true }) {
        for ((index, childRequest) in request.getChildRequests().withIndex()) {
          val content = buildBlockContent(viewer, context, childRequest.request, childRequest.path, childRequest.fileStatus, needTakeTool)
                        ?: continue
          viewer.addChildBlock(content, index > 0)
        }
      }

      private fun buildBlockContent(viewer: CombinedDiffViewer,
                                    context: DiffContext,
                                    request: DiffRequest,
                                    path: FilePath,
                                    fileStatus: FileStatus,
                                    needTakeTool: (FrameDiffTool) -> Boolean = { true }): CombinedDiffBlockContent? {
        val diffSettings = getSettings(context.getUserData(DiffUserDataKeys.PLACE))
        val diffTools = DiffManagerEx.getInstance().diffTools
        request.putUserData(DiffUserDataKeys.ALIGNED_TWO_SIDED_DIFF, true)

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

        return CombinedDiffBlockContent(childViewer, path, fileStatus)
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
