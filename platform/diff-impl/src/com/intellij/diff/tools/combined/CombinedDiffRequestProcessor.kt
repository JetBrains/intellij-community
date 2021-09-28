// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff.tools.combined

import com.intellij.diff.*
import com.intellij.diff.chains.DiffRequestProducer
import com.intellij.diff.impl.CacheDiffRequestProcessor
import com.intellij.diff.impl.DiffRequestProcessor
import com.intellij.diff.impl.DiffSettingsHolder
import com.intellij.diff.impl.DiffSettingsHolder.DiffSettings.Companion.getSettings
import com.intellij.diff.requests.DiffRequest
import com.intellij.diff.tools.fragmented.UnifiedDiffTool
import com.intellij.diff.util.DiffUserDataKeys
import com.intellij.diff.util.DiffUserDataKeysEx.ScrollToPolicy
import com.intellij.diff.util.DiffUtil
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project

private val LOG = logger<CombinedDiffRequestProcessor>()

interface CombinedDiffRequestProducer : DiffRequestProducer {
  fun getFilesSize(): Int
}

open class CombinedDiffRequestProcessor(project: Project?,
                                        private val requestProducer: CombinedDiffRequestProducer) :
  CacheDiffRequestProcessor.Simple(project) {

  override fun getCurrentRequestProvider(): DiffRequestProducer = requestProducer

  protected val viewer get() = activeViewer as? CombinedDiffViewer
  protected val request get() = activeRequest as? CombinedDiffRequest

  //
  // Navigation
  //

  final override fun isNavigationEnabled(): Boolean = requestProducer.getFilesSize() > 0

  final override fun hasNextChange(fromUpdate: Boolean): Boolean {
    val curFilesIndex = viewer?.scrollSupport?.blockIterable?.index ?: -1
    return curFilesIndex != -1 && curFilesIndex < requestProducer.getFilesSize() - 1
  }

  final override fun hasPrevChange(fromUpdate: Boolean): Boolean {
    val curFilesIndex = viewer?.scrollSupport?.blockIterable?.index ?: -1
    return curFilesIndex != -1 && curFilesIndex > 0
  }

  final override fun goToNextChange(fromDifferences: Boolean) {
    goToChange(fromDifferences, true)

    updateRequest(false, if (fromDifferences) ScrollToPolicy.FIRST_CHANGE else null) //needed to apply myIterationState = IterationState.NONE; (press again to go to next/previous file)
  }

  final override fun goToPrevChange(fromDifferences: Boolean) {
    goToChange(fromDifferences, false)

    updateRequest(false, if (fromDifferences) ScrollToPolicy.LAST_CHANGE else null) //needed to apply myIterationState = IterationState.NONE; (press again to go to next/previous file)
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
  // Combined diff builder logic
  //

  class CombinedDiffViewerBuilder : DiffExtension() {

    override fun onViewerCreated(viewer: FrameDiffTool.DiffViewer, context: DiffContext, request: DiffRequest) {

      if (request !is CombinedDiffRequest) return
      if (viewer !is CombinedDiffViewer) return

      buildCombinedDiffChildViewers(viewer, context, request)
    }

    companion object {
      fun buildCombinedDiffChildViewers(viewer: CombinedDiffViewer,
                                        context: DiffContext,
                                        request: CombinedDiffRequest,
                                        needTakeTool: (FrameDiffTool) -> Boolean = { true }) {
        val diffSettings = getSettings(context.getUserData(DiffUserDataKeys.PLACE))
        val diffTools = DiffManagerEx.getInstance().diffTools

        for ((index, childRequest) in request.requests.withIndex()) {
          val childDiffRequest = childRequest.request
          childDiffRequest.putUserData(DiffUserDataKeys.ALIGNED_TWO_SIDED_DIFF, true)
          val frameDiffTool =
            if (viewer.unifiedDiff && UnifiedDiffTool.INSTANCE.canShow(context, childDiffRequest)) {
              UnifiedDiffTool.INSTANCE
            }
            else {
              getDiffToolsExceptUnified(context, diffSettings, diffTools, childDiffRequest, needTakeTool)
            }

          val childViewer = frameDiffTool
                              ?.let { findSubstitutor(it, context, childDiffRequest) }
                              ?.createComponent(context, childDiffRequest)
                            ?: continue

          EP_NAME.forEachExtensionSafe { extension ->
            try {
              extension.onViewerCreated(childViewer, context, childDiffRequest)
            }
            catch (e: Throwable) {
              LOG.error(e)
            }
          }

          val content = CombinedDiffBlockContent(childViewer, childRequest.path, childRequest.fileStatus)
          viewer.addChildBlock(content, index > 0)
        }
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
