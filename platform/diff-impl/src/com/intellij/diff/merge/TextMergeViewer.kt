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
import com.intellij.openapi.util.BooleanGetter
import com.intellij.openapi.util.Disposer
import org.jetbrains.annotations.ApiStatus
import javax.swing.Action
import javax.swing.JComponent

@ApiStatus.Internal
open class TextMergeViewer(
  private val mergeContext: MergeContext,
  private val mergeRequest: TextMergeRequest,
) : MergeViewer {
  val viewer: MergeThreesideViewer

  private val cancelResolveAction: Action?
  private val leftResolveAction: Action?
  private val rightResolveAction: Action?
  private val acceptResolveAction: Action?

  init {
    val diffContext: DiffContext = ProxyDiffContext(mergeContext)
    val diffRequest: ContentDiffRequest = ProxySimpleDiffRequest(mergeRequest.getTitle(),
                                                                 getDiffContents(mergeRequest),
                                                                 getDiffContentTitles(mergeRequest),
                                                                 mergeRequest)
    diffRequest.putUserData(DiffUserDataKeys.FORCE_READ_ONLY_CONTENTS, booleanArrayOf(true, false, true))

    viewer = loadThreeSideViewer(diffContext, diffRequest, mergeContext, mergeRequest, this)

    cancelResolveAction = viewer.getLoadedResolveAction(MergeResult.CANCEL)
    leftResolveAction = viewer.getLoadedResolveAction(MergeResult.LEFT)
    rightResolveAction = viewer.getLoadedResolveAction(MergeResult.RIGHT)
    acceptResolveAction = viewer.getLoadedResolveAction(MergeResult.RESOLVED)
  }

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

    components.closeHandler = BooleanGetter {
      val exit = MergeUtil.showExitWithoutApplyingChangesDialog(this, mergeRequest, mergeContext, viewer.isContentModified)
      viewer.logMergeCancelled(viewer.myContentModified, exit)
      exit
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
    val conflictResolver = LangSpecificMergeConflictResolverWrapper(context.getProject(), mergeRequest.getContents())

    val project = context.getProject()
    val model = MergeConflictModel(project, mergeRequest, conflictResolver)
    val builder = MergeDiffBuilder(project, mergeRequest, conflictResolver)

    Disposer.register(this, model)
    return MergeThreesideViewer(context, request, mergeContext, mergeRequest, mergeViewer, builder, conflictResolver, model)
  }

  companion object {
    private fun getDiffContents(mergeRequest: TextMergeRequest): List<DiffContent?> {
      val contents = mergeRequest.getContents()

      val left = ThreeSide.LEFT.select<DocumentContent>(contents)
      val right = ThreeSide.RIGHT.select<DocumentContent>(contents)
      val output = mergeRequest.getOutputContent()

      return listOf<DiffContent>(left, output, right)
    }

    private fun getDiffContentTitles(mergeRequest: TextMergeRequest): MutableList<String?> {
      val titles = MergeUtil.notNullizeContentTitles(mergeRequest.getContentTitles())
      titles[ThreeSide.BASE.index] = DiffBundle.message("merge.version.title.merged.result")
      return titles
    }
  }
}
