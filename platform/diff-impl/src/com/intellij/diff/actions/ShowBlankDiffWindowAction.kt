// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff.actions

import com.intellij.diff.*
import com.intellij.diff.chains.DiffRequestChainBase
import com.intellij.diff.chains.DiffRequestProducer
import com.intellij.diff.contents.DocumentContent
import com.intellij.diff.contents.FileContent
import com.intellij.diff.requests.DiffRequest
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.diff.tools.simple.SimpleDiffTool
import com.intellij.diff.tools.util.DiffDataKeys
import com.intellij.diff.tools.util.side.TwosideTextDiffViewer
import com.intellij.diff.util.DiffUserDataKeysEx
import com.intellij.diff.util.Side
import com.intellij.ide.dnd.FileCopyPasteUtil
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.editor.EditorDropHandler
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileEditor.impl.EditorWindow
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolder
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.io.File

class ShowBlankDiffWindowAction : DumbAwareAction() {
  init {
    isEnabledInModalContext = true
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project

    var defaultText: String? = null
    val editor = e.getData(CommonDataKeys.EDITOR)
    if (editor != null && editor.selectionModel.hasSelection()) {
      defaultText = editor.selectionModel.selectedText
    }

    val content1 = createEditableContent(project, StringUtil.notNullize(defaultText))
    val content2 = createEditableContent(project, "")

    val chain = BlankWindowDiffRequestChain(content1, content2)
    chain.putUserData(DiffUserDataKeysEx.FORCE_DIFF_TOOL, SimpleDiffTool.INSTANCE)

    DiffManager.getInstance().showDiff(project, chain, DiffDialogHints.DEFAULT)
  }
}

internal class SwitchToBlankEditorAction : BlankActionBase() {
  override fun isEnabled(viewer: TwosideTextDiffViewer, chain: BlankWindowDiffRequestChain, context: DiffContextEx, side: Side): Boolean {
    val content = side.select(chain.content1, chain.content2)
    return content is FileContent
  }

  override fun perform(viewer: TwosideTextDiffViewer, chain: BlankWindowDiffRequestChain, context: DiffContextEx, side: Side) {
    val newContent = createEditableContent(viewer.project, "")
    chain.setContent(newContent, side)

    context.reloadDiffRequest()
  }
}

internal class SwitchToFileEditorAction : BlankActionBase() {
  override fun isEnabled(viewer: TwosideTextDiffViewer, chain: BlankWindowDiffRequestChain, context: DiffContextEx, side: Side): Boolean {
    return true
  }

  override fun perform(viewer: TwosideTextDiffViewer, chain: BlankWindowDiffRequestChain, context: DiffContextEx, side: Side) {
    val descriptor = FileChooserDescriptor(true, false, false, false, false, false)
    descriptor.title = "Select File to Compare"
    descriptor.description = ""
    val file = FileChooser.chooseFile(descriptor, viewer.component, viewer.project, null) ?: return

    val newContent = createFileContent(viewer.project, file) ?: return
    chain.setContent(newContent, side)

    context.reloadDiffRequest()
  }
}

internal abstract class BlankActionBase : DumbAwareAction() {
  override fun update(e: AnActionEvent) {
    val viewer = e.getData(DiffDataKeys.DIFF_VIEWER) as? TwosideTextDiffViewer
    val request = e.getData(DiffDataKeys.DIFF_REQUEST) as? BlankWindowDiffRequest
    val context = e.getData(DiffDataKeys.DIFF_CONTEXT) as? DiffContextEx
    if (viewer == null || request == null || context == null) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    val editor = e.getData(CommonDataKeys.EDITOR)
    val side = Side.fromValue(viewer.editors, editor)
    if (side == null) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    e.presentation.isEnabledAndVisible = isEnabled(viewer, request.chain, context, side)
  }

  override fun actionPerformed(e: AnActionEvent) {
    val viewer = e.getData(DiffDataKeys.DIFF_VIEWER) as TwosideTextDiffViewer
    val request = e.getData(DiffDataKeys.DIFF_REQUEST) as BlankWindowDiffRequest
    val context = e.getData(DiffDataKeys.DIFF_CONTEXT) as DiffContextEx

    val editor = e.getData(CommonDataKeys.EDITOR)
    val side = Side.fromValue(viewer.editors, editor)!!

    perform(viewer, request.chain, context, side)
  }

  protected abstract fun isEnabled(viewer: TwosideTextDiffViewer,
                                   chain: BlankWindowDiffRequestChain,
                                   context: DiffContextEx,
                                   side: Side): Boolean

  protected abstract fun perform(viewer: TwosideTextDiffViewer,
                                 chain: BlankWindowDiffRequestChain,
                                 context: DiffContextEx,
                                 side: Side)
}

class ShowBlankDiffWindowDiffExtension : DiffExtension() {
  override fun onViewerCreated(viewer: FrameDiffTool.DiffViewer, context: DiffContext, request: DiffRequest) {
    if (request is BlankWindowDiffRequest &&
        context is DiffContextEx &&
        viewer is TwosideTextDiffViewer) {
      DnDHandler(viewer, context, request.chain, Side.LEFT).install()
      DnDHandler(viewer, context, request.chain, Side.RIGHT).install()
    }
  }
}

private class DnDHandler(val viewer: TwosideTextDiffViewer,
                         val context: DiffContextEx,
                         val chain: BlankWindowDiffRequestChain,
                         val side: Side) : EditorDropHandler {
  fun install() {
    val editor = viewer.getEditor(side) as? EditorImpl ?: return
    editor.setDropHandler(this)
  }

  override fun canHandleDrop(transferFlavors: Array<out DataFlavor>): Boolean {
    return FileCopyPasteUtil.isFileListFlavorAvailable(transferFlavors)
  }

  override fun handleDrop(t: Transferable, project: Project?, editorWindow: EditorWindow?) {
    val success = doHandleDnD(t)
    if (success) context.reloadDiffRequest()
  }

  private fun doHandleDnD(transferable: Transferable): Boolean {
    val files = FileCopyPasteUtil.getFileList(transferable)
    if (files != null) {
      if (files.size == 1) {
        val newContent = createFileContent(viewer.project, files[0]) ?: return false
        chain.setContent(newContent, side)
        return true
      }
      if (files.size >= 2) {
        val newContent1 = createFileContent(viewer.project, files[0]) ?: return false
        val newContent2 = createFileContent(viewer.project, files[1]) ?: return false
        chain.setContent(newContent1, Side.LEFT)
        chain.setContent(newContent2, Side.RIGHT)
        return true
      }
    }
    return false
  }
}

private fun createEditableContent(project: Project?, text: String): DocumentContent {
  return DiffContentFactory.getInstance().createEditable(project, text, null)
}

private fun createFileContent(project: Project?, file: File): DocumentContent? {
  val virtualFile = VfsUtil.findFileByIoFile(file, true) ?: return null
  return createFileContent(project, virtualFile)
}

private fun createFileContent(project: Project?, file: VirtualFile): DocumentContent? {
  return DiffContentFactory.getInstance().createDocument(project, file)
}

internal class BlankWindowDiffRequestChain(var content1: DocumentContent, var content2: DocumentContent) : DiffRequestChainBase() {
  var title1: String? = getTitleFor(content1)
  var title2: String? = getTitleFor(content2)

  override fun getRequests(): List<DiffRequestProducer> {
    return listOf(object : DiffRequestProducer {
      override fun getName(): String {
        return "Change"
      }

      override fun process(context: UserDataHolder, indicator: ProgressIndicator): DiffRequest {
        return BlankWindowDiffRequest(this@BlankWindowDiffRequestChain)
      }
    })
  }

  fun setContent(newContent: DocumentContent, side: Side) {
    val title = getTitleFor(newContent)

    if (side.isLeft) {
      content1 = newContent
      title1 = title
    }
    else {
      content2 = newContent
      title2 = title
    }
  }

  private fun getTitleFor(content: DocumentContent) =
    if (content is FileContent) DiffRequestFactory.getInstance().getContentTitle(content.file) else null
}

private class BlankWindowDiffRequest internal constructor(val chain: BlankWindowDiffRequestChain)
  : SimpleDiffRequest(null, chain.content1, chain.content2, chain.title1, chain.title2)
