// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.actions

import com.intellij.diff.*
import com.intellij.diff.FrameDiffTool.DiffViewer
import com.intellij.diff.actions.impl.MutableDiffRequestChain
import com.intellij.diff.contents.DiffContent
import com.intellij.diff.contents.DocumentContent
import com.intellij.diff.contents.FileContent
import com.intellij.diff.requests.ContentDiffRequest
import com.intellij.diff.requests.DiffRequest
import com.intellij.diff.tools.simple.SimpleDiffTool
import com.intellij.diff.tools.util.DiffDataKeys
import com.intellij.diff.tools.util.DiffNotifications
import com.intellij.diff.tools.util.base.DiffViewerBase
import com.intellij.diff.tools.util.base.DiffViewerListener
import com.intellij.diff.tools.util.side.ThreesideTextDiffViewer
import com.intellij.diff.tools.util.side.TwosideTextDiffViewer
import com.intellij.diff.util.*
import com.intellij.icons.AllIcons
import com.intellij.ide.CopyPasteManagerEx
import com.intellij.ide.IdeEventQueue
import com.intellij.ide.dnd.FileCopyPasteUtil
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.diff.DiffBundle
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorDropHandler
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileEditor.impl.EditorWindow
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.JBPopupMenu
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.UIBundle
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.containers.LinkedListWithSum
import com.intellij.util.containers.map2Array
import com.intellij.util.text.DateFormatUtil
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.event.MouseEvent
import java.io.File
import javax.swing.JComponent
import kotlin.math.max

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

    val chain = BlankDiffWindowUtil.createBlankDiffRequestChain(content1, content2, baseContent)
    chain.putUserData(DiffUserDataKeys.PLACE, "BlankDiffWindow")
    chain.putUserData(DiffUserDataKeysEx.FORCE_DIFF_TOOL, SimpleDiffTool.INSTANCE)
    chain.putUserData(DiffUserDataKeysEx.PREFERRED_FOCUS_SIDE, Side.LEFT)
    chain.putUserData(DiffUserDataKeysEx.DISABLE_CONTENTS_EQUALS_NOTIFICATION, true)

    DiffManagerEx.getInstance().showDiffBuiltin(project, chain, DiffDialogHints.DEFAULT)
  }
}

internal class SwitchToBlankEditorAction : BlankSwitchContentActionBase() {
  override fun isEnabled(currentContent: DiffContent): Boolean = currentContent is FileContent

  override fun createNewContent(project: Project?, contextComponent: JComponent): DiffContent {
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

internal class SwitchToRecentEditorActionGroup : ActionGroup(), DumbAware {
  override fun update(e: AnActionEvent) {
    val helper = MutableDiffRequestChain.createHelper(e.dataContext)
    if (helper == null) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    if (helper.chain.getUserData(BlankDiffWindowUtil.BLANK_KEY) != true) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    e.presentation.isEnabledAndVisible = true
  }

  override fun getChildren(e: AnActionEvent?): Array<AnAction> {
    return BlankDiffWindowUtil.getRecentFiles().map2Array { MySwitchAction(it) }
  }

  @Suppress("DialogTitleCapitalization")
  private class MySwitchAction(val content: RecentBlankContent) : BlankSwitchContentActionBase() {
    init {
      val text = content.text
      val dateAppendix = DateFormatUtil.formatPrettyDateTime(content.timestamp)
      val presentable = when {
        text.length < 40 -> DiffBundle.message("blank.diff.recent.content.summary.text.date", text.trim(), dateAppendix)
        else -> {
          val shortenedText = StringUtil.shortenTextWithEllipsis(text.trim(), 30, 0)
          DiffBundle.message("blank.diff.recent.content.summary.text.length.date", shortenedText, text.length, dateAppendix)
        }
      }
      templatePresentation.text = presentable
    }

    override fun isEnabled(currentContent: DiffContent): Boolean = true

    override fun createNewContent(project: Project?, contextComponent: JComponent): DiffContent {
      return createEditableContent(project, content.text)
    }
  }
}

internal abstract class BlankSwitchContentActionBase : DumbAwareAction() {
  override fun update(e: AnActionEvent) {
    val helper = MutableDiffRequestChain.createHelper(e.dataContext)
    if (helper == null) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    if (helper.chain.getUserData(BlankDiffWindowUtil.BLANK_KEY) != true) {
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
    val editor = e.getRequiredData(CommonDataKeys.EDITOR)
    val viewer = e.getRequiredData(DiffDataKeys.DIFF_VIEWER)
    perform(editor, viewer, helper)
  }

  fun perform(editor: Editor, viewer: DiffViewer, helper: MutableDiffRequestChain.Helper) {
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

  abstract fun isEnabled(currentContent: DiffContent): Boolean

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

    if (helper.chain.getUserData(BlankDiffWindowUtil.BLANK_KEY) != true) {
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
  override fun onViewerCreated(viewer: DiffViewer, context: DiffContext, request: DiffRequest) {
    val helper = MutableDiffRequestChain.createHelper(context, request) ?: return
    if (helper.chain.getUserData(BlankDiffWindowUtil.BLANK_KEY) != true) return

    if (viewer is TwosideTextDiffViewer) {
      DnDHandler2(viewer, helper, Side.LEFT).install()
      DnDHandler2(viewer, helper, Side.RIGHT).install()
    }
    else if (viewer is ThreesideTextDiffViewer) {
      DnDHandler3(viewer, helper, ThreeSide.LEFT).install()
      DnDHandler3(viewer, helper, ThreeSide.BASE).install()
      DnDHandler3(viewer, helper, ThreeSide.RIGHT).install()
    }

    if (viewer is DiffViewerBase) {
      RecentContentHandler(viewer, helper).install()
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

private class RecentContentHandler(val viewer: DiffViewerBase,
                                   val helper: MutableDiffRequestChain.Helper) {
  fun install() {
    viewer.addListener(MyListener())
  }

  private inner class MyListener : DiffViewerListener() {
    override fun onDispose() {
      BlankDiffWindowUtil.saveRecentContents(viewer.request)
    }
  }
}

internal data class RecentBlankContent(val text: @NlsSafe String, val timestamp: Long)

object BlankDiffWindowUtil {
  val BLANK_KEY = Key.create<Boolean>("Diff.BlankWindow")

  @JvmField
  val REMEMBER_CONTENT_KEY = Key.create<Boolean>("Diff.BlankWindow.BlankContent")

  @JvmStatic
  fun createBlankDiffRequestChain(content1: DocumentContent,
                                  content2: DocumentContent,
                                  baseContent: DocumentContent? = null): MutableDiffRequestChain {
    val chain = MutableDiffRequestChain(content1, baseContent, content2)
    chain.putUserData(BLANK_KEY, true)
    return chain
  }


  private val ourRecentFiles = LinkedListWithSum<RecentBlankContent> { it.text.length }

  internal fun getRecentFiles(): List<RecentBlankContent> = ourRecentFiles.toList()

  @RequiresEdt
  fun saveRecentContents(request: DiffRequest) {
    if (request is ContentDiffRequest) {
      for (content in request.contents) {
        saveRecentContent(content)
      }
    }
  }

  @RequiresEdt
  fun saveRecentContent(content: DiffContent) {
    if (content !is DocumentContent) return
    if (!DiffUtil.isUserDataFlagSet(REMEMBER_CONTENT_KEY, content)) return

    val text = content.document.text
    if (text.isBlank()) return

    val oldValue = ourRecentFiles.find { it.text == text }
    if (oldValue != null) {
      ourRecentFiles.remove(oldValue)
      ourRecentFiles.add(0, oldValue)
    }
    else {
      ourRecentFiles.add(0, RecentBlankContent(text, System.currentTimeMillis()))
      deleteAfterAllowedMaximum()
    }
  }

  private fun deleteAfterAllowedMaximum() {
    val maxCount = max(1, Registry.intValue("blank.diff.history.max.items"))
    val maxMemory = max(0, Registry.intValue("blank.diff.history.max.memory"))
    CopyPasteManagerEx.deleteAfterAllowedMaximum(ourRecentFiles, maxCount, maxMemory) { item ->
      RecentBlankContent(UIBundle.message("clipboard.history.purged.item"), item.timestamp)
    }
  }
}

private fun createEditableContent(project: Project?, text: String = ""): DocumentContent {
  val content = DiffContentFactoryEx.getInstanceEx().documentContent(project, false).buildFromText(text, false)
  content.putUserData(BlankDiffWindowUtil.REMEMBER_CONTENT_KEY, true)
  DiffUtil.addNotification(DiffNotificationProvider { viewer -> createBlankNotificationProvider(viewer, content) }, content)
  return content
}

private fun createFileContent(project: Project?, file: File): DocumentContent? {
  val virtualFile = VfsUtil.findFileByIoFile(file, true) ?: return null
  return createFileContent(project, virtualFile)
}

private fun createFileContent(project: Project?, file: VirtualFile): DocumentContent? {
  return DiffContentFactory.getInstance().createDocument(project, file)
}

private fun createBlankNotificationProvider(viewer: DiffViewer?, content: DocumentContent): JComponent? {
  if (viewer !is DiffViewerBase) return null
  val helper = MutableDiffRequestChain.createHelper(viewer.context, viewer.request) ?: return null

  val editor = when (viewer) {
    is TwosideTextDiffViewer -> {
      val index = viewer.contents.indexOf(content)
      if (index == -1) return null
      viewer.editors[index]
    }
    is ThreesideTextDiffViewer -> {
      val index = viewer.contents.indexOf(content)
      if (index == -1) return null
      viewer.editors[index]
    }
    else -> return null
  }
  if (editor.document.textLength != 0) return null

  val panel = EditorNotificationPanel(editor, null, null)
  panel.createActionLabel(DiffBundle.message("notification.action.text.blank.diff.select.file")) {
    SwitchToFileEditorAction().perform(editor, viewer, helper)
  }
  if (BlankDiffWindowUtil.getRecentFiles().isNotEmpty()) {
    panel.createActionLabel(DiffBundle.message("notification.action.text.blank.diff.recent")) {
      val menu = ActionManager.getInstance().createActionPopupMenu(ActionPlaces.UNKNOWN, SwitchToRecentEditorActionGroup())
      menu.setTargetComponent(editor.component)
      val event = IdeEventQueue.getInstance().trueCurrentEvent
      if (event is MouseEvent) {
        JBPopupMenu.showByEvent(event, menu.component)
      }
      else {
        JBPopupMenu.showByEditor(editor, menu.component)
      }
    }.apply {
      setIcon(AllIcons.General.LinkDropTriangle)
      isIconAtRight = true
      setUseIconAsLink(true)
    }
  }

  editor.document.addDocumentListener(object : DocumentListener {
    override fun documentChanged(event: DocumentEvent) {
      editor.document.removeDocumentListener(this)
      DiffNotifications.hideNotification(panel)
    }
  }, viewer)
  return panel
}

