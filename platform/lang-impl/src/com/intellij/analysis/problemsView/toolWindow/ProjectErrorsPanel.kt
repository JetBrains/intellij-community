// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.analysis.problemsView.toolWindow

import com.intellij.analysis.problemsView.ProblemsCollector
import com.intellij.openapi.actionSystem.ToggleOptionAction.Option
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

internal class ProjectErrorsPanel(project: Project, state: ProblemsViewState)
  : ProblemsViewPanel(project, state) {

  init {
    treeModel.root = ProjectErrorsRoot(this)
    tree.emptyText.text = ProblemsViewBundle.message("problems.view.project.empty")
  }

  override fun getDisplayName() = ProblemsViewBundle.message("problems.view.project")
  override fun getSortFoldersFirst(): Option? = null
  override fun getSortBySeverity(): Option? = null
}


private class ProjectErrorsRoot(panel: ProblemsViewPanel) : Root(panel) {
  private val collector = ProblemsCollector.getInstance(panel.project)

  override fun getProblemCount() = collector.getProblemCount()
  override fun getProblemFiles() = collector.getProblemFiles()

  override fun getFileProblemCount(file: VirtualFile) = collector.getFileProblemCount(file)
  override fun getFileProblems(file: VirtualFile) = collector.getFileProblems(file)

  override fun getOtherProblemCount() = collector.getOtherProblemCount()
  override fun getOtherProblems() = collector.getOtherProblems()
}
