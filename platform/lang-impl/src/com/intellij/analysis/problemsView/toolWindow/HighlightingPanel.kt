// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.analysis.problemsView.toolWindow

import com.intellij.analysis.problemsView.AnalysisProblemBundle.message
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.openapi.actionSystem.ToggleOptionAction.Option
import com.intellij.openapi.fileEditor.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.ui.tree.TreeUtil.promiseExpand

internal class HighlightingPanel(project: Project, state: ProblemsViewState) : ProblemsViewPanel(project, state) {

  init {
    project.messageBus.connect(this).subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, object : FileEditorManagerListener {
      override fun fileOpened(manager: FileEditorManager, file: VirtualFile) = updateCurrentFile()
      override fun fileClosed(manager: FileEditorManager, file: VirtualFile) = updateCurrentFile()
      override fun selectionChanged(event: FileEditorManagerEvent) = updateCurrentFile()
    })
  }

  public override fun getDisplayName() = message("problems.view.highlighting")

  public override fun getShowErrors(): Option? = null

  public override fun getSortFoldersFirst(): Option? = null

  public override fun selectionChangedTo(selected: Boolean) {
    super.selectionChangedTo(selected)
    if (selected) updateCurrentFile()
  }

  fun selectHighlightInfo(info: HighlightInfo) {
    val root = treeModel.root as? HighlightingFileRoot
    val node = root?.findProblemNode(info)
    if (node != null) select(node)
  }

  private fun updateCurrentFile() {
    val file = findCurrentFile()
    val root = treeModel.root as? HighlightingFileRoot
    if (file == null) {
      if (root == null) return
      treeModel.root = null
    }
    else {
      if (root != null && root.file == file) return
      treeModel.root = HighlightingFileRoot(this, file)
    }
    promiseExpand(tree, 2) // TODO: expand node without root handle automatically
    updateDisplayName()
  }

  private fun findCurrentFile(): VirtualFile? {
    if (project.isDisposed) return null
    val fileEditor = FileEditorManager.getInstance(project)?.selectedEditor ?: return null
    val file = fileEditor.file
    if (file != null) return file
    val textEditor = fileEditor as? TextEditor ?: return null
    return FileDocumentManager.getInstance().getFile(textEditor.editor.document)
  }
}
