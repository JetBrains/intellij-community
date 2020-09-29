// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff.actions

import com.intellij.diff.*
import com.intellij.diff.actions.impl.MutableDiffRequestChain
import com.intellij.diff.contents.DiffContent
import com.intellij.diff.contents.DocumentContent
import com.intellij.diff.contents.FileContent
import com.intellij.diff.requests.DiffRequest
import com.intellij.diff.tools.simple.SimpleDiffTool
import com.intellij.diff.tools.util.DiffDataKeys
import com.intellij.diff.tools.util.base.DiffViewerBase
import com.intellij.diff.tools.util.side.ThreesideTextDiffViewer
import com.intellij.diff.tools.util.side.TwosideTextDiffViewer
import com.intellij.diff.util.DiffUserDataKeysEx
import com.intellij.diff.util.Side
import com.intellij.diff.util.ThreeSide
import com.intellij.ide.dnd.FileCopyPasteUtil
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.diff.DiffBundle
import com.intellij.openapi.editor.EditorDropHandler
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileEditor.impl.EditorWindow
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.io.File
import javax.swing.JComponent

private val BLANK_KEY = Key.create<Boolean>("Diff.BlankWindow")

class ShowBlankDiffWindowAction : DumbAwareAction() {
  init {
    isEnabledInModalContext = true
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project
    val editor = e.getData(CommonDataKeys.EDITOR)
    val files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)

    val content1: DocumentContent
    val content2: DocumentContent
    var baseContent: DocumentContent? = null

    if (files != null && files.size == 3) {
      content1 = createFileContent(project, files[0]) ?: createEditableContent(project)
      baseContent = createFileContent(project, files[1]) ?: createEditableContent(project)
      content2 = createFileContent(project, files[2]) ?: createEditableContent(project)
    }
    else if (files != null && files.size == 2) {
      content1 = createFileContent(project, files[0]) ?: createEditableContent(project)
      content2 = createFileContent(project, files[1]) ?: createEditableContent(project)
    }
    else if (editor != null && editor.selectionModel.hasSelection()) {
      val defaultText = editor.selectionModel.selectedText ?: ""
      content1 = createEditableContent(project, defaultText)
      content2 = createEditableContent(project)
    }
    else {
      content1 = createEditableContent(project)
      content2 = createEditableContent(project)
    }

    val chain = if (baseContent != null) {
      MutableDiffRequestChain(content1, baseContent, content2)
    }
    else {
      MutableDiffRequestChain(content1, content2)
    }
    chain.putUserData(DiffUserDataKeysEx.FORCE_DIFF_TOOL, SimpleDiffTool.INSTANCE)
    chain.putUserData(BLANK_KEY, true)

    DiffManager.getInstance().showDiff(project, chain, DiffDialogHints.DEFAULT)
  }
}

internal class SwitchToBlankEditorAction : BlankSwitchContentActionBase() {
  override fun isEnabled(currentContent: DiffContent): Boolean = currentContent is FileContent

  override fun createNewContent(project: Project?, contextComponent: JComponent): DiffContent? {
    return createEditableContent(project, "")
  }
}

internal class SwitchToFileEditorAction : BlankSwitchContentActionBase() {
  override fun isEnabled(currentContent: DiffContent): Boolean = true

  override fun createNewContent(project: Project?, contextComponent: JComponent): DiffContent? {
    val descriptor = FileChooserDescriptor(true, false, false, false, false, false)
    descriptor.title = DiffBundle.message("select.file.to.compare")
    descriptor.description = ""
    val file = FileChooser.chooseFile(descriptor, contextComponent, project, null) ?: return null

    return createFileContent(project, file)
  }
}

internal abstract class BlankSwitchContentActionBase : DumbAwareAction() {
  override fun update(e: AnActionEvent) {
    val helper = MutableDiffRequestChain.createHelper(e.dataContext)
    if (helper == null) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    if (helper.chain.getUserData(BLANK_KEY) != true) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    val editor = e.getData(CommonDataKeys.EDITOR)
    val viewer = e.getData(DiffDataKeys.DIFF_VIEWER)
    if (viewer is TwosideTextDiffViewer) {
      val side = Side.fromValue(viewer.editors, editor)
      val currentContent = side?.select(helper.chain.content1, helper.chain.content2)
      e.presentation.isEnabledAndVisible = currentContent != null && isEnabled(currentContent)
    }
    else if (viewer is ThreesideTextDiffViewer) {
      val side = ThreeSide.fromValue(viewer.editors, editor)
      val currentContent = side?.select(helper.chain.content1, helper.chain.baseContent, helper.chain.content2)
      e.presentation.isEnabledAndVisible = currentContent != null && isEnabled(currentContent)
    }
    else {
      e.presentation.isEnabledAndVisible = false
    }
  }

  override fun actionPerformed(e: AnActionEvent) {
    val helper = MutableDiffRequestChain.createHelper(e.dataContext)!!

    val editor = e.getData(CommonDataKeys.EDITOR)
    val viewer = e.getData(DiffDataKeys.DIFF_VIEWER)
    if (viewer is TwosideTextDiffViewer) {
      val side = Side.fromValue(viewer.editors, editor) ?: return
      val newContent = createNewContent(viewer.project, viewer.component) ?: return
      helper.setContent(newContent, side)
    }
    else if (viewer is ThreesideTextDiffViewer) {
      val side = ThreeSide.fromValue(viewer.editors, editor) ?: return
      val newContent = createNewContent(viewer.project, viewer.component) ?: return
      helper.setContent(newContent, side)
    }

    helper.fireRequestUpdated()
  }

  protected abstract fun isEnabled(currentContent: DiffContent): Boolean

  protected abstract fun createNewContent(project: Project?, contextComponent: JComponent): DiffContent?
}

internal class BlankToggleThreeSideModeAction : DumbAwareAction() {
  override fun update(e: AnActionEvent) {
    val helper = MutableDiffRequestChain.createHelper(e.dataContext)
    val viewer = e.getData(DiffDataKeys.DIFF_VIEWER) as? DiffViewerBase
    if (helper == null || viewer == null) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    if (helper.chain.getUserData(BLANK_KEY) != true) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    e.presentation.text = if (helper.chain.baseContent != null) {
      ActionsBundle.message("action.ToggleThreeSideInBlankDiffWindow.text.disable")
    }
    else {
      ActionsBundle.message("action.ToggleThreeSideInBlankDiffWindow.text.enable")
    }
    e.presentation.isEnabledAndVisible = true
  }

  override fun actionPerformed(e: AnActionEvent) {
    val helper = MutableDiffRequestChain.createHelper(e.dataContext)!!
    val viewer = e.getRequiredData(DiffDataKeys.DIFF_VIEWER) as DiffViewerBase

    if (helper.chain.baseContent != null) {
      helper.chain.baseContent = null
      helper.chain.baseTitle = null
    }
    else {
      helper.setContent(createEditableContent(viewer.project), ThreeSide.BASE)
    }
    helper.fireRequestUpdated()
  }
}


class ShowBlankDiffWindowDiffExtension : DiffExtension() {
  override fun onViewerCreated(viewer: FrameDiffTool.DiffViewer, context: DiffContext, request: DiffRequest) {
    val helper = MutableDiffRequestChain.createHelper(context, request) ?: return
    if (helper.chain.getUserData(BLANK_KEY) != true) return

    if (viewer is TwosideTextDiffViewer) {
      DnDHandler2(viewer, helper, Side.LEFT).install()
      DnDHandler2(viewer, helper, Side.RIGHT).install()
    }
    else if (viewer is ThreesideTextDiffViewer) {
      DnDHandler3(viewer, helper, ThreeSide.LEFT).install()
      DnDHandler3(viewer, helper, ThreeSide.BASE).install()
      DnDHandler3(viewer, helper, ThreeSide.RIGHT).install()
    }
  }
}

private class DnDHandler2(val viewer: TwosideTextDiffViewer,
                          val helper: MutableDiffRequestChain.Helper,
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
    if (success) helper.fireRequestUpdated()
  }

  private fun doHandleDnD(transferable: Transferable): Boolean {
    val files = FileCopyPasteUtil.getFileList(transferable)
    if (files != null) {
      if (files.size == 1) {
        val newContent = createFileContent(viewer.project, files[0]) ?: return false
        helper.setContent(newContent, side)
        return true
      }
      if (files.size >= 2) {
        val newContent1 = createFileContent(viewer.project, files[0]) ?: return false
        val newContent2 = createFileContent(viewer.project, files[1]) ?: return false
        helper.setContent(newContent1, Side.LEFT)
        helper.setContent(newContent2, Side.RIGHT)
        return true
      }
    }
    return false
  }
}

private class DnDHandler3(val viewer: ThreesideTextDiffViewer,
                          val helper: MutableDiffRequestChain.Helper,
                          val side: ThreeSide) : EditorDropHandler {
  fun install() {
    val editor = viewer.getEditor(side) as? EditorImpl ?: return
    editor.setDropHandler(this)
  }

  override fun canHandleDrop(transferFlavors: Array<out DataFlavor>): Boolean {
    return FileCopyPasteUtil.isFileListFlavorAvailable(transferFlavors)
  }

  override fun handleDrop(t: Transferable, project: Project?, editorWindow: EditorWindow?) {
    val success = doHandleDnD(t)
    if (success) helper.fireRequestUpdated()
  }

  private fun doHandleDnD(transferable: Transferable): Boolean {
    val files = FileCopyPasteUtil.getFileList(transferable)
    if (files != null) {
      if (files.size == 1) {
        val newContent = createFileContent(viewer.project, files[0]) ?: return false
        helper.setContent(newContent, side)
        return true
      }
      if (files.size == 3) {
        val newContent1 = createFileContent(viewer.project, files[0]) ?: return false
        val newBaseContent = createFileContent(viewer.project, files[1]) ?: return false
        val newContent2 = createFileContent(viewer.project, files[2]) ?: return false
        helper.setContent(newContent1, ThreeSide.LEFT)
        helper.setContent(newBaseContent, ThreeSide.BASE)
        helper.setContent(newContent2, ThreeSide.RIGHT)
        return true
      }
    }
    return false
  }
}

private fun createEditableContent(project: Project?, text: String = ""): DocumentContent {
  return DiffContentFactoryEx.getInstanceEx().documentContent(project, false).buildFromText(text, false)
}

private fun createFileContent(project: Project?, file: File): DocumentContent? {
  val virtualFile = VfsUtil.findFileByIoFile(file, true) ?: return null
  return createFileContent(project, virtualFile)
}

private fun createFileContent(project: Project?, file: VirtualFile): DocumentContent? {
  return DiffContentFactory.getInstance().createDocument(project, file)
}
