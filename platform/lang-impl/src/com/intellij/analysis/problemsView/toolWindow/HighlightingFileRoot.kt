// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.analysis.problemsView.toolWindow

import com.intellij.analysis.problemsView.Problem
import com.intellij.analysis.problemsView.ProblemsProvider
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.ex.RangeHighlighterEx
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile

internal class HighlightingFileRoot(panel: ProblemsViewPanel, val file: VirtualFile) : Root(panel) {

  private val problems = mutableSetOf<HighlightingProblem>()
  private val filter = ProblemFilter(panel.state)

  private val provider = object : ProblemsProvider {
    override val project = panel.project
  }

  private val watcher = createWatcher(provider, file)

  init {
    Disposer.register(this, provider)
    Disposer.register(provider, watcher)
  }

  fun findProblem(highlighter: RangeHighlighterEx) = watcher.findProblem(highlighter)

  override fun getProblemCount() = synchronized(problems) { problems.count(filter) }

  override fun getProblemFiles() = when (getProblemCount() > 0) {
    true -> listOf(file)
    else -> emptyList()
  }

  override fun getFileProblemCount(file: VirtualFile) = when (this.file == file) {
    true -> getProblemCount()
    else -> 0
  }

  override fun getFileProblems(file: VirtualFile) = when (this.file == file) {
    true -> synchronized(problems) { problems.filter(filter) }
    else -> emptyList()
  }

  private fun createWatcher(provider: ProblemsProvider,
                            file: VirtualFile): HighlightingWatcher =
    HighlightingWatcher(provider, this, file, HighlightSeverity.INFORMATION.myVal + 1)

  override fun getOtherProblemCount() = 0

  override fun getOtherProblems(): Collection<Problem> = emptyList()

  override fun problemAppeared(problem: Problem) {
    if (problem !is HighlightingProblem || problem.file != file) return
    notify(problem, synchronized(problems) { SetUpdateState.add(problem, problems) })
    if (Registry.`is`("wolf.the.problem.solver")) return
    // start filling HighlightingErrorsProvider if WolfTheProblemSolver is disabled
    if (problem.severity < HighlightSeverity.ERROR.myVal) return
    HighlightingErrorsProviderBase.getInstance(problem.provider.project).problemsAppeared(file)
  }

  override fun problemDisappeared(problem: Problem) {
    if (problem !is HighlightingProblem || problem.file != file) return
    notify(problem, synchronized(problems) { SetUpdateState.remove(problem, problems) })
  }

  override fun problemUpdated(problem: Problem) {
    if (problem !is HighlightingProblem || problem.file != file) return
    notify(problem, synchronized(problems) { SetUpdateState.update(problem, problems) })
  }

  private fun notify(problem: Problem, state: SetUpdateState) = when (state) {
    SetUpdateState.ADDED -> super.problemAppeared(problem)
    SetUpdateState.REMOVED -> super.problemDisappeared(problem)
    SetUpdateState.UPDATED -> super.problemUpdated(problem)
    SetUpdateState.IGNORED -> {
    }
  }

  override fun getChildren(node: FileNode): Collection<Node> = when {
    !panel.state.groupByToolId -> super.getChildren(node)
    else -> getFileProblems(node.file)
      .groupBy { it.group }
      .flatMap { entry ->
        entry.key?.let {
          listOf(GroupNode(node, it, entry.value))
        }
        ?: getNodesForProblems(entry.value.map { Pair(node, it) })
    }
  }
}