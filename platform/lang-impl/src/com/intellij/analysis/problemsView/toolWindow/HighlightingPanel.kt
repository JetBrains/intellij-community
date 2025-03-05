// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.analysis.problemsView.toolWindow

import com.intellij.codeWithMe.ClientId
import com.intellij.ide.PowerSaveMode
import com.intellij.ide.TreeExpander
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.ToggleOptionAction.Option
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ex.EditorMarkupModel
import com.intellij.openapi.editor.ex.RangeHighlighterEx
import com.intellij.openapi.editor.markup.AnalyzingType
import com.intellij.openapi.fileEditor.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.SingleAlarm
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.ui.tree.TreeUtil
import org.jetbrains.annotations.Nls
import org.jetbrains.concurrency.CancellablePromise

class HighlightingPanel(project: Project, state: ProblemsViewState)
  : ProblemsViewPanel(project, ID, state, ProblemsViewBundle.messagePointer("problems.view.highlighting")),
    FileEditorManagerListener, PowerSaveMode.Listener {

  companion object {
    const val ID: String = "CurrentFile"
  }

  private val statusUpdateAlarm: SingleAlarm = SingleAlarm.pooledThreadSingleAlarm(delay = 200, parentDisposable = this) { updateStatus() }
  @Volatile
  private var previousStatus: Status? = null

  init {
    ThreadingAssertions.assertEventDispatchThread()
    tree.showsRootHandles = false
    project.messageBus.connect(this)
      .subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, this)
    ApplicationManager.getApplication().messageBus.connect(this)
      .subscribe(PowerSaveMode.TOPIC, this)
  }

  override fun getSortFoldersFirst(): Option? = null
  override fun getTreeExpander(): TreeExpander? = null

  override fun uiDataSnapshot(sink: DataSink) {
    super.uiDataSnapshot(sink)
    sink[CommonDataKeys.VIRTUAL_FILE] = getCurrentFile()
  }

  override fun getSortBySeverity(): Option? {
    return mySortBySeverity
  }

  override fun selectionChangedTo(selected: Boolean) {
    super.selectionChangedTo(selected)
    if (selected) updateSelectedFile()
  }

  override fun powerSaveStateChanged() {
    statusUpdateAlarm.cancelAndRequest(forceRun = true)
    updateToolWindowContent()
  }

  override fun fileOpened(manager: FileEditorManager, file: VirtualFile) {
    updateCurrentFileIfLocalId()
  }
  override fun fileClosed(manager: FileEditorManager, file: VirtualFile) {
    updateCurrentFileIfLocalId()
  }
  override fun selectionChanged(event: FileEditorManagerEvent) {
    updateCurrentFileIfLocalId()
  }

  /**
   * CWM-768: If a new editor is selected from a CodeWithMe client,
   * then this view should ignore such event
   */
  private fun updateCurrentFileIfLocalId() {
    if (ClientId.current == session.clientId) {
      updateSelectedFile()
    }
  }
  
  fun updateSelectedFile(): CancellablePromise<*> {
    return ReadAction.nonBlocking {
      if (!myDisposed) {
        ClientId.withClientId(session.clientId) {
          ApplicationManager.getApplication().assertIsNonDispatchThread()
          ApplicationManager.getApplication().assertReadAccessAllowed()
          setCurrentFile(findSelectedFile())
        }
      }
    }.submit(AppExecutorUtil.getAppExecutorService())
  }

  internal val currentRoot: ProblemsViewHighlightingFileRoot?
    get() = treeModel.root as? ProblemsViewHighlightingFileRoot

  private fun getCurrentDocument(): Document? = currentRoot?.document

  fun setCurrentFile(pair: Pair<VirtualFile, Document>?) {
    if (pair == null) {
      if (treeModel.root == null) return
      treeModel.root = null
    }
    else {
      val (file, document) = pair
      if (currentRoot?.file == file) return
      treeModel.root = ProblemsViewHighlightingFileRoot(this, file, document)
      TreeUtil.promiseSelectFirstLeaf(tree)
    }
    powerSaveStateChanged()
  }
  fun getCurrentFile(): VirtualFile? = currentRoot?.file

  fun selectHighlighter(highlighter: RangeHighlighterEx) {
    val problem = currentRoot?.findProblem(highlighter) ?: return
    TreeUtil.promiseSelect(tree, ProblemNodeFinder(problem))
  }

  private fun findSelectedFile(): Pair<VirtualFile, Document>? {
    ApplicationManager.getApplication().assertIsNonDispatchThread()
    ApplicationManager.getApplication().assertReadAccessAllowed()
    if (project.isDisposed) return null
    val fileEditor = FileEditorManager.getInstance(project)?.selectedEditor ?: return null
    var virtualFile: VirtualFile?
    val document: Document?
    if (fileEditor is TextEditor) {
      document = fileEditor.editor.document
      virtualFile = fileEditor.editor.virtualFile
      if (virtualFile == null) {
        virtualFile = FileDocumentManager.getInstance().getFile(document)
      }
    } else {
      virtualFile = fileEditor.file
      document = if (virtualFile == null) null else FileDocumentManager.getInstance().getDocument(virtualFile)
    }
    if (virtualFile != null && document != null) return Pair(virtualFile, document)
    return null
  }

  @RequiresBackgroundThread
  private fun updateStatus() {
    ApplicationManager.getApplication().assertIsNonDispatchThread()
    val status = ClientId.withClientId(session.clientId) { ReadAction.compute(ThrowableComputable { getCurrentStatus() })}
    if (previousStatus != status) {
      ApplicationManager.getApplication().invokeLater {
        if (!myDisposed) {
          previousStatus = status
          tree.emptyText.text = status.title
          if (status.details.isNotEmpty()) tree.emptyText.appendLine(status.details)
        }
      }
    }
    if (status.request) {
      statusUpdateAlarm.cancelAndRequest()
    }
  }

  @RequiresBackgroundThread
  @RequiresReadLock
  private fun getCurrentStatus(): Status {
    ApplicationManager.getApplication().assertIsNonDispatchThread()
    val file = getCurrentFile() ?: return Status(ProblemsViewBundle.message("problems.view.highlighting.no.selected.file"))
    if (PowerSaveMode.isEnabled()) return Status(ProblemsViewBundle.message("problems.view.highlighting.power.save.mode"))
    val document = getCurrentDocument() ?: return statusAnalyzing(file)

    // todo ijpl-339 it's not safe to take a random editor anymore
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
