// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.analysis.problemsView.toolWindow

import com.intellij.analysis.problemsView.FileProblem
import com.intellij.analysis.problemsView.Problem
import com.intellij.analysis.problemsView.ProblemsListener
import com.intellij.openapi.actionSystem.ToggleOptionAction.Option
import com.intellij.openapi.project.Project

internal class ProjectErrorsPanel(project: Project, state: ProblemsViewState)
  : ProblemsViewPanel(project, state), ProblemsListener {

  private val root = Root(this)

  init {
    treeModel.root = root
    tree.emptyText.text = ProblemsViewBundle.message("problems.view.project.empty")
  }

  override fun getDisplayName() = ProblemsViewBundle.message("problems.view.project")
  override fun getSortFoldersFirst(): Option? = null
  override fun getSortBySeverity(): Option? = null

  override fun problemAppeared(problem: Problem) {
    if (problem is FileProblem) root.addProblems(problem.file, problem)
  }

  override fun problemDisappeared(problem: Problem) {
    if (problem is FileProblem) root.removeProblems(problem.file, problem)
  }

  override fun problemUpdated(problem: Problem) {
    if (problem is FileProblem) root.updateProblem(problem.file, problem)
  }
}
