// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.analysis.problemsView.toolWindow

import com.intellij.analysis.problemsView.Problem
import com.intellij.analysis.problemsView.ProblemsListener
import com.intellij.analysis.problemsView.ProblemsProvider
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.ex.RangeHighlighterEx
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile

internal class HighlightingFileRoot(panel: ProblemsViewPanel, val file: VirtualFile)
  : Root(panel, ProblemFilter(panel.state)), ProblemsListener {

  private val provider = object : ProblemsProvider {
    override val project = panel.project
  }

  private val watcher = HighlightingWatcher(provider, this, file, HighlightSeverity.INFORMATION.myVal + 1)

  init {
    Disposer.register(this, provider)
    Disposer.register(provider, watcher)
  }

  fun findProblemNode(highlighter: RangeHighlighterEx): ProblemNode? {
    val problem = watcher.findProblem(highlighter) ?: return null
    return super.findProblemNode(file, problem)
  }

  override fun problemAppeared(problem: Problem) {
    addProblems(file, problem)
    if (!ProblemsView.isProjectErrorsEnabled()) return
    if (problem is HighlightingProblem && problem.severity >= HighlightSeverity.ERROR.myVal) {
      HighlightingErrorsProvider.getInstance(problem.provider.project).problemsAppeared(file)
    }
  }

  override fun problemDisappeared(problem: Problem) {
    removeProblems(file, problem)
  }

  override fun problemUpdated(problem: Problem) {
    updateProblem(file, problem)
  }
}
