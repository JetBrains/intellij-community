// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.merge

import com.intellij.diff.DiffContext
import com.intellij.diff.contents.DiffContent
import com.intellij.diff.contents.DocumentContent
import com.intellij.diff.merge.MergeTool.MergeViewer
import com.intellij.diff.merge.MergeUtil.ProxyDiffContext
import com.intellij.diff.requests.ContentDiffRequest
import com.intellij.diff.requests.ProxySimpleDiffRequest
import com.intellij.diff.util.DiffUserDataKeys
import com.intellij.diff.util.ThreeSide
import com.intellij.openapi.diff.DiffBundle
import com.intellij.openapi.util.Disposer
import org.jetbrains.annotations.ApiStatus
import javax.swing.Action
import javax.swing.JComponent

@ApiStatus.Internal
open class TextMergeViewer(
  private val mergeContext: MergeContext,
  private val mergeRequest: TextMergeRequest,
) : MergeViewer {
  val viewer: MergeThreesideViewer = createMergeThreesideViewer(mergeContext, mergeRequest)

  private val cancelResolveAction = viewer.getLoadedResolveAction(MergeResult.CANCEL)
  private val leftResolveAction = viewer.getLoadedResolveAction(MergeResult.LEFT)
  private val rightResolveAction = viewer.getLoadedResolveAction(MergeResult.RIGHT)
  private val acceptResolveAction = viewer.getLoadedResolveAction(MergeResult.RESOLVED)

  //
  // Impl
  //
  override fun getComponent(): JComponent = viewer.component

  override fun getPreferredFocusedComponent(): JComponent? = viewer.getPreferredFocusedComponent()

  override fun init(): MergeTool.ToolbarComponents {
    val components = MergeTool.ToolbarComponents()

    val init = viewer.init()
    components.statusPanel = init.statusPanel
    components.toolbarActions = init.toolbarActions

    components.closeHandler = {
      MergeUtil.showExitWithoutApplyingChangesDialog(this, mergeRequest, mergeContext, viewer.isContentModified).also {
        viewer.logMergeCancelled(viewer.myContentModified, it)
      }
    }

    return components
  }

  override fun getResolveAction(result: MergeResult): Action? {
    return when (result) {
      MergeResult.CANCEL -> cancelResolveAction
      MergeResult.LEFT -> leftResolveAction
      MergeResult.RIGHT -> rightResolveAction
      MergeResult.RESOLVED -> acceptResolveAction
    }
  }

  override fun dispose() {
    Disposer.dispose(this.viewer)
  }

  //
  // Viewer
  //
  protected open fun loadThreeSideViewer(
    context: DiffContext,
    request: ContentDiffRequest,
    mergeContext: MergeContext,
    mergeRequest: TextMergeRequest,
    mergeViewer: TextMergeViewer,
  ): MergeThreesideViewer {
    return MergeThreesideViewer(context, request, mergeContext, mergeRequest, mergeViewer)
  }

  private fun createMergeThreesideViewer(mergeContext: MergeContext, mergeRequest: TextMergeRequest): MergeThreesideViewer {
    val diffContext = ProxyDiffContext(mergeContext)
    val diffRequest = ProxySimpleDiffRequest(mergeRequest.getTitle(),
                                             getDiffContents(mergeRequest),
                                             getDiffContentTitles(mergeRequest),
                                             mergeRequest)
    diffRequest.putUserData(DiffUserDataKeys.FORCE_READ_ONLY_CONTENTS, booleanArrayOf(true, false, true))

    return loadThreeSideViewer(diffContext, diffRequest, mergeContext, mergeRequest, this)
  }

  companion object {
    private fun getDiffContents(mergeRequest: TextMergeRequest): List<DiffContent> {
      val contents = mergeRequest.getContents()

      val left = ThreeSide.LEFT.select<DocumentContent>(contents)
      val right = ThreeSide.RIGHT.select<DocumentContent>(contents)
      val output = mergeRequest.getOutputContent()

      return listOf<DiffContent>(left, output, right)
    }

    private fun getDiffContentTitles(mergeRequest: TextMergeRequest): List<String> {
      val titles = MergeUtil.notNullizeContentTitles(mergeRequest.getContentTitles())
      titles[ThreeSide.BASE.index] = DiffBundle.message("merge.version.title.merged.result")
      return titles
    }
  }
}
