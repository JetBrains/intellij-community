// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.merge

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.ExtensionPointName.Companion.create
import com.intellij.util.concurrency.annotations.RequiresEdt
import javax.swing.Action
import javax.swing.JComponent

interface MergeTool {
  /**
   * Creates viewer for the given request. Clients should call [.canShow] first.
   */
  @RequiresEdt
  fun createComponent(context: MergeContext, request: MergeRequest): MergeViewer

  fun canShow(context: MergeContext, request: MergeRequest): Boolean

  /**
   * Merge viewer should call [MergeContext.finishMerge] when processing is over.
   *
   *
   * [MergeRequest.applyResult] will be performed by the caller, so it shouldn't be called by MergeViewer directly.
   */
  interface MergeViewer : Disposable {
    /**
     * The component will be used for [com.intellij.openapi.actionSystem.ActionToolbar.setTargetComponent]
     * and might want to implement [com.intellij.openapi.actionSystem.UiDataProvider] for [ToolbarComponents.toolbarActions].
     */
    fun getComponent(): JComponent
    fun getPreferredFocusedComponent(): JComponent?

    /**
     * @return Action that should be triggered on the corresponding action.
     *
     *
     * Typical implementation can perform some checks and either call finishMerge(result) or do nothing
     *
     *
     * return null if action is not available
     */
    fun getResolveAction(result: MergeResult): Action?

    /**
     * Should be called after adding [.getComponent] to the components hierarchy.
     */
    @RequiresEdt
    fun init(): ToolbarComponents

    @RequiresEdt
    override fun dispose()
  }

  class ToolbarComponents {
    @JvmField
    var toolbarActions: List<AnAction>? = null
    @JvmField
    var statusPanel: JComponent? = null

    /**
     * Return false if a merge window should be prevented from closing and canceling resolve.
     */
    @JvmField
    var closeHandler: (() -> Boolean)? = null
  }

  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<MergeTool> = create("com.intellij.diff.merge.MergeTool")
  }
}
