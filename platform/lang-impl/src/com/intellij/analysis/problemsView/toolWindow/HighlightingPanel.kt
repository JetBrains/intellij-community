// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.analysis.problemsView.toolWindow

import com.intellij.codeWithMe.ClientId
import com.intellij.icons.AllIcons.Toolwindows
import com.intellij.ide.PowerSaveMode
import com.intellij.ide.TreeExpander
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.ToggleOptionAction.Option
import com.intellij.openapi.application.ApplicationManager.getApplication
import com.intellij.openapi.application.ModalityState.stateForComponent
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ex.EditorMarkupModel
import com.intellij.openapi.editor.ex.RangeHighlighterEx
import com.intellij.openapi.editor.markup.AnalyzingType
import com.intellij.openapi.fileEditor.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.SingleAlarm
import com.intellij.util.ui.tree.TreeUtil
import org.jetbrains.annotations.Nls
import javax.swing.Icon

class HighlightingPanel(project: Project, state: ProblemsViewState)
  : ProblemsViewPanel(project, ID, state, ProblemsViewBundle.messagePointer("problems.view.highlighting")),
    FileEditorManagerListener, PowerSaveMode.Listener {

  companion object {
    const val ID = "CurrentFile"
  }

  private val statusUpdateAlarm = SingleAlarm(Runnable(this::updateStatus), 200, stateForComponent(this), this)
  private var previousStatus: Status? = null

  init {
    tree.showsRootHandles = false
    updateCurrentFile()
    project.messageBus.connect(this)
      .subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, this)
    getApplication().messageBus.connect(this)
      .subscribe(PowerSaveMode.TOPIC, this)
  }

  override fun getSortFoldersFirst(): Option? = null
  override fun getTreeExpander(): TreeExpander? = null

  override fun getData(dataId: String): Any? {
    if (CommonDataKeys.VIRTUAL_FILE.`is`(dataId)) return currentFile
    return super.getData(dataId)
  }

  override fun selectionChangedTo(selected: Boolean) {
    super.selectionChangedTo(selected)
    if (selected) updateCurrentFile()
  }

  override fun powerSaveStateChanged() {
    statusUpdateAlarm.cancelAndRequest(forceRun = true)
    updateToolWindowContent()
  }

  override fun fileOpened(manager: FileEditorManager, file: VirtualFile) = updateCurrentFileIfLocalId()
  override fun fileClosed(manager: FileEditorManager, file: VirtualFile) = updateCurrentFileIfLocalId()
  override fun selectionChanged(event: FileEditorManagerEvent) = updateCurrentFileIfLocalId()

  /**
   * CWM-768: If a new editor is selected from a CodeWithMe client,
   * then this view should ignore such event
   */
  private fun updateCurrentFileIfLocalId() {
    if (ClientId.current == myClientId) {
      updateCurrentFile()
    }
  }
  
  private fun updateCurrentFile() {
    currentFile = ClientId.withClientId(myClientId) { findCurrentFile() }
  }

  internal val currentRoot
    get() = treeModel.root as? HighlightingFileRoot

  var currentFile
    get() = currentRoot?.file
    set(file) {
      if (file == null) {
        if (currentRoot == null) return
        treeModel.root = null
      }
      else {
        if (currentRoot?.file == file) return
        treeModel.root = getRoot(file)
        TreeUtil.promiseSelectFirstLeaf(tree)
      }
      powerSaveStateChanged()
    }

  internal fun getRoot(file: VirtualFile): HighlightingFileRoot = HighlightingFileRoot(this, file)

  fun selectHighlighter(highlighter: RangeHighlighterEx) {
    val problem = currentRoot?.findProblem(highlighter) ?: return
    TreeUtil.promiseSelect(tree, ProblemNodeFinder(problem))
  }

  private fun findCurrentFile(): VirtualFile? {
    if (project.isDisposed) return null
    val fileEditor = FileEditorManager.getInstance(project)?.selectedEditor ?: return null
    val file = fileEditor.file
    if (file != null) return file
    val textEditor = fileEditor as? TextEditor ?: return null
    return FileDocumentManager.getInstance().getFile(textEditor.editor.document)
  }

  private fun updateStatus() {
    val status = ClientId.withClientId(myClientId) { getCurrentStatus() }
    if (previousStatus != status) {
      previousStatus = status
      tree.emptyText.text = status.title
      if (status.details.isNotEmpty()) tree.emptyText.appendLine(status.details)
    }
    if (status.request) statusUpdateAlarm.cancelAndRequest()
  }

  private fun getCurrentStatus(): Status {
    val file = currentFile ?: return Status(ProblemsViewBundle.message("problems.view.highlighting.no.selected.file"))
    if (PowerSaveMode.isEnabled()) return Status(ProblemsViewBundle.message("problems.view.highlighting.power.save.mode"))
    val document = ProblemsView.getDocument(project, file) ?: return statusAnalyzing(file)
    val editor = EditorFactory.getInstance().editors(document, project).findFirst().orElse(null) ?: return statusAnalyzing(file)
    val model = editor.markupModel as? EditorMarkupModel ?: return statusAnalyzing(file)
    val status = model.errorStripeRenderer?.status ?: return statusComplete(file)
    return when (status.analyzingType) {
      AnalyzingType.SUSPENDED -> Status(status.title, status.details, request = true)
      AnalyzingType.COMPLETE -> statusComplete(file, state.hideBySeverity.isNotEmpty())
      AnalyzingType.PARTIAL -> statusAnalyzing(file, state.hideBySeverity.isNotEmpty())
      else -> statusAnalyzing(file)
    }
  }

  private fun statusAnalyzing(file: VirtualFile, filtered: Boolean = false): Status {
    val title = ProblemsViewBundle.message("problems.view.highlighting.problems.analyzing", file.name)
    if (filtered) {
      val details = ProblemsViewBundle.message("problems.view.highlighting.problems.not.found.filter")
      return Status(title, details, request = true)
    }
    return Status(title, request = true)
  }

  private fun statusComplete(file: VirtualFile, filtered: Boolean = false): Status {
    val title = ProblemsViewBundle.message("problems.view.highlighting.problems.not.found", file.name)
    if (filtered) {
      val details = ProblemsViewBundle.message("problems.view.highlighting.problems.not.found.filter")
      return Status(title, details)
    }
    return Status(title)
  }
}

private data class Status(@Nls val title: String, @Nls val details: String = "", val request: Boolean = false)
