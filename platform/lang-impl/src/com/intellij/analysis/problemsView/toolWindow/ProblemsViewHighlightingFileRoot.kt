// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.analysis.problemsView.toolWindow

import com.intellij.analysis.problemsView.Problem
import com.intellij.analysis.problemsView.ProblemsProvider
import com.intellij.codeInsight.multiverse.CodeInsightContext
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.ex.RangeHighlighterEx
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile

internal class ProblemsViewHighlightingFileRoot(panel: ProblemsViewPanel, val file: VirtualFile, val document: Document) : Root(panel) {

  private val problems = mutableSetOf<HighlightingProblem>()
  private val filter = ProblemFilter(panel.state)

  private val provider: ProblemsProvider = object : ProblemsProvider {
    override val project = panel.project
  }

  private val watcher: ProblemsViewHighlightingWatcher = ProblemsViewHighlightingWatcher(provider, this, file, document, HighlightSeverity.TEXT_ATTRIBUTES.myVal + 1)

  init {
    Disposer.register(this, provider)
  }

  fun findProblem(highlighter: RangeHighlighterEx): Problem? = watcher.findProblem(highlighter)

  override fun getProblemCount(): Int = synchronized(problems) { problems.count(filter) }

  override fun getProblemFiles(): List<VirtualFile> = when (getProblemCount() > 0) {
    true -> listOf(file)
    else -> emptyList()
  }

  override fun getFileProblemCount(file: VirtualFile): Int = when (this.file == file) {
    true -> getProblemCount()
    else -> 0
  }

  override fun getFileProblems(file: VirtualFile): List<HighlightingProblem> = when (this.file == file) {
    true -> synchronized(problems) {
      problems.filter(filter)
    }
    else -> emptyList()
  }

  override fun getOtherProblemCount(): Int = 0

  override fun getOtherProblems(): Collection<Problem> = emptyList()

  override fun problemAppeared(problem: Problem) {
    if (problem !is HighlightingProblem || problem.file != file) {
      return
    }
    notify(problem = problem, state = synchronized(problems) { SetUpdateState.add(problem, problems) })
  }

  override fun problemDisappeared(problem: Problem) {
    if (problem is HighlightingProblem && problem.file == file) {
      notify(problem, synchronized(problems) { SetUpdateState.remove(problem, problems) })
    }
  }

  override fun problemUpdated(problem: Problem) {
    if (problem is HighlightingProblem && problem.file == file) {
      notify(problem, synchronized(problems) { SetUpdateState.update(problem, problems) })
    }
  }

  private fun notify(problem: Problem, state: SetUpdateState) {
    when (state) {
      SetUpdateState.ADDED -> super.problemAppeared(problem)
      SetUpdateState.REMOVED -> super.problemDisappeared(problem)
      SetUpdateState.UPDATED -> super.problemUpdated(problem)
      SetUpdateState.IGNORED -> {
      }
    }
  }

  private fun getContextGroups(node: FileNode): Map<CodeInsightContext?, List<HighlightingProblem>> {
    return getFileProblems(node.file).groupBy { it.contextGroup }
  }

  private fun getAmountOfContexts(node: FileNode): Int {
    return getContextGroups(node).size
  }

  private fun getFileNodesWithContext(node: FileNode): Collection<Node> {
    return getContextGroups(node)
      .flatMap { (group, problems) ->
        group?.let {
          listOf(ProblemsContextNode(node, it, problems) { panel.state.groupByToolId })
        }
        ?: getNodesForProblems(node, problems)
      }
  }

  private fun getFileNodesWithoutContext(node: FileNode): Collection<Node> {
    return getFileProblems(node.file)
      .groupBy { it.group }
      .flatMap { (group, problems) ->
        if (group != null) {
          listOf(ProblemsViewGroupNode(node, group, problems))
        }
        else {
          getNodesForProblems(node, problems)
        }
      }
  }

  override fun getChildren(node: FileNode): Collection<Node> = when {
    getAmountOfContexts(node) > 1 ->
      getFileNodesWithContext(node)
    !panel.state.groupByToolId ->
      super.getChildren(node)
    else -> getFileNodesWithoutContext(node)
  }
}