// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff

import com.intellij.diff.chains.DiffRequestChain
import com.intellij.diff.chains.SimpleDiffRequestChain
import com.intellij.diff.editor.ChainDiffVirtualFile
import com.intellij.diff.editor.DiffEditorTabFilesManager.Companion.getInstance
import com.intellij.diff.impl.DiffRequestPanelImpl
import com.intellij.diff.impl.DiffWindow
import com.intellij.diff.merge.BinaryMergeTool
import com.intellij.diff.merge.MergeRequest
import com.intellij.diff.merge.MergeRequestHandler
import com.intellij.diff.merge.MergeRequestProducer
import com.intellij.diff.merge.MergeTool
import com.intellij.diff.merge.MergeWindow.ForProducer
import com.intellij.diff.merge.MergeWindow.ForRequest
import com.intellij.diff.merge.TextMergeTool
import com.intellij.diff.merge.ThreesideMergeRequest
import com.intellij.diff.merge.external.AutomaticExternalMergeTool
import com.intellij.diff.requests.DiffRequest
import com.intellij.diff.tools.binary.BinaryDiffTool
import com.intellij.diff.tools.dir.DirDiffTool
import com.intellij.diff.tools.external.ExternalDiffSettings
import com.intellij.diff.tools.external.ExternalDiffSettings.Companion.findMergeTool
import com.intellij.diff.tools.external.ExternalDiffTool
import com.intellij.diff.tools.external.ExternalDiffTool.showIfNeeded
import com.intellij.diff.tools.external.ExternalMergeTool
import com.intellij.diff.tools.external.ExternalMergeTool.show
import com.intellij.diff.tools.fragmented.UnifiedDiffTool
import com.intellij.diff.tools.simple.SimpleDiffTool
import com.intellij.diff.util.DiffUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.diff.DiffBundle
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.WindowWrapper
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.ApiStatus
import java.awt.Window

@ApiStatus.Internal
open class DiffManagerImpl : DiffManagerEx() {
  override fun showDiff(project: Project?, request: DiffRequest) {
    showDiff(project, request, DiffDialogHints.DEFAULT)
  }

  override fun showDiff(project: Project?, request: DiffRequest, hints: DiffDialogHints) {
    val requestChain: DiffRequestChain = SimpleDiffRequestChain(request)
    showDiff(project, requestChain, hints)
  }

  override fun showDiff(project: Project?, requests: DiffRequestChain, hints: DiffDialogHints) {
    if (ExternalDiffTool.isEnabled() && showIfNeeded(project, requests, hints)) {
        return
    }

    showDiffBuiltin(project, requests, hints)
  }

  override fun showDiffBuiltin(project: Project?, request: DiffRequest) {
    showDiffBuiltin(project, request, DiffDialogHints.DEFAULT)
  }

  override fun showDiffBuiltin(project: Project?, request: DiffRequest, hints: DiffDialogHints) {
    val requestChain: DiffRequestChain = SimpleDiffRequestChain(request)
    showDiffBuiltin(project, requestChain, hints)
  }

  override fun showDiffBuiltin(project: Project?, requests: DiffRequestChain, hints: DiffDialogHints) {
    val diffEditorTabFilesManager = if (project != null) getInstance(project) else null
    if (diffEditorTabFilesManager != null &&
        !Registry.`is`("show.diff.as.frame") &&
        DiffUtil.getWindowMode(hints) == WindowWrapper.Mode.FRAME &&
        !isFromDialog(project) &&
        hints.windowConsumer == null
    ) {
      val diffFile = ChainDiffVirtualFile(requests, DiffBundle.message("label.default.diff.editor.tab.name"))
      diffEditorTabFilesManager.showDiffFile(diffFile, true)
      return
    }
    DiffWindow(project, requests, hints).show()
  }

  override fun createRequestPanel(project: Project?, parent: Disposable, window: Window?): DiffRequestPanel {
    val panel = DiffRequestPanelImpl(project, window)
    Disposer.register(parent, panel)
    return panel
  }

  override fun getDiffTools(): List<DiffTool> = buildList {
    addAll(DiffTool.EP_NAME.extensionList)
    add(SimpleDiffTool.INSTANCE)
    add(UnifiedDiffTool.INSTANCE)
    add(BinaryDiffTool.INSTANCE)
    add(DirDiffTool.INSTANCE)
  }

  override fun getMergeTools(): List<MergeTool> = buildList {
    addAll(MergeTool.EP_NAME.extensionList)
    add(TextMergeTool.INSTANCE)
    add(BinaryMergeTool.INSTANCE)
  }

  @ApiStatus.Internal
  override fun getHandler(project: Project?, request: MergeRequest): MergeRequestHandler {
    // plugin may provide a better tool for this MergeRequest
    val plugin = AutomaticExternalMergeTool.EP_NAME.findFirstSafe { mergeTool -> mergeTool.canShow(project, request) }
    if (plugin != null) return MergeRequestHandler.ExtensionBasedHandler(plugin)

    if (request is ThreesideMergeRequest) {
      val fileType = request.getOutputContent().getContentType()
      if (fileType != null && ExternalMergeTool.isEnabled()) {
        val mergeTool = findMergeTool(fileType)
        if (mergeTool != null && ExternalMergeTool.canShow(request)) {
          return MergeRequestHandler.UserConfiguredExternalToolHandler(mergeTool)
        }
      }
    }
    return MergeRequestHandler.BuiltInHandler
  }

  @RequiresEdt
  override fun showMerge(project: Project?, request: MergeRequest) {
    when (val handler = getHandler(project, request)) {
      MergeRequestHandler.BuiltInHandler -> showMergeBuiltin(project, request)
      is MergeRequestHandler.UserConfiguredExternalToolHandler -> show(project,
                                                                       handler.tool as ExternalDiffSettings.ExternalTool,
                                                                       request as ThreesideMergeRequest)
      is MergeRequestHandler.ExtensionBasedHandler -> handler.plugin.show(project, request)
    }
  }

  @RequiresEdt
  override fun showMergeBuiltin(project: Project?, request: MergeRequest) {
    ForRequest(project, request, DiffDialogHints.MODAL).show()
  }

  @RequiresEdt
  override fun showMergeBuiltin(project: Project?, requestProducer: MergeRequestProducer, hints: DiffDialogHints) {
    ForProducer(project, requestProducer, hints).show()
  }

  private fun isFromDialog(project: Project?): Boolean {
    return DialogWrapper.findInstance(IdeFocusManager.getInstance(project).getFocusOwner()) != null
  }
}
