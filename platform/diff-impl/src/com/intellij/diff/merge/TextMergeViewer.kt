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
import java.util.*
import javax.swing.Action
import javax.swing.JComponent

@ApiStatus.Internal
open class TextMergeViewer(private val myMergeContext: MergeContext, private val myMergeRequest: TextMergeRequest) : MergeViewer {
  //
  // Getters
  //
  val viewer: MergeThreesideViewer

  private val myCancelResolveAction: Action?
  private val myLeftResolveAction: Action?
  private val myRightResolveAction: Action?
  private val myAcceptResolveAction: Action?

  init {
    val diffContext: DiffContext = ProxyDiffContext(myMergeContext)
    val diffRequest: ContentDiffRequest = ProxySimpleDiffRequest(
      myMergeRequest.getTitle(),
      getDiffContents(myMergeRequest),
      getDiffContentTitles(myMergeRequest),
      myMergeRequest
    )
    diffRequest.putUserData<BooleanArray?>(DiffUserDataKeys.FORCE_READ_ONLY_CONTENTS, booleanArrayOf(true, false, true))

    this.viewer = loadThreeSideViewer(diffContext, diffRequest, myMergeContext, myMergeRequest, this)

    myCancelResolveAction = viewer.getLoadedResolveAction(MergeResult.CANCEL)
    myLeftResolveAction = viewer.getLoadedResolveAction(MergeResult.LEFT)
    myRightResolveAction = viewer.getLoadedResolveAction(MergeResult.RIGHT)
    myAcceptResolveAction = viewer.getLoadedResolveAction(MergeResult.RESOLVED)
  }

  val component: JComponent
    //
    get() = viewer.getComponent()

  val preferredFocusedComponent: JComponent?
    get() = viewer.getPreferredFocusedComponent()

  override fun init(): MergeTool.ToolbarComponents {
    val components = MergeTool.ToolbarComponents()

    val init = viewer.init()
    components.statusPanel = init.statusPanel
    components.toolbarActions = init.toolbarActions

    components.closeHandler = BooleanGetter {
      val exit = MergeUtil.showExitWithoutApplyingChangesDialog(this, myMergeRequest, myMergeContext, viewer.isContentModified())
      viewer.logMergeCancelled(viewer.myContentModified, exit)
      exit
    }

    return components
  }

  override fun getResolveAction(result: MergeResult): Action? {
    return when (result) {
      MergeResult.CANCEL -> myCancelResolveAction
      MergeResult.LEFT -> myLeftResolveAction
      MergeResult.RIGHT -> myRightResolveAction
      MergeResult.RESOLVED -> myAcceptResolveAction
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

  companion object {
    private fun getDiffContents(mergeRequest: TextMergeRequest): MutableList<DiffContent?> {
      val contents = mergeRequest.getContents()

      val left = ThreeSide.LEFT.select<DocumentContent?>(contents)
      val right = ThreeSide.RIGHT.select<DocumentContent?>(contents)
      val output = mergeRequest.getOutputContent()

      return Arrays.asList<DiffContent?>(left, output, right)
    }

    private fun getDiffContentTitles(mergeRequest: TextMergeRequest): MutableList<String?> {
      val titles = MergeUtil.notNullizeContentTitles(mergeRequest.getContentTitles())
      titles.set(ThreeSide.BASE.index, DiffBundle.message("merge.version.title.merged.result"))
      return titles
    }
  }
}
