// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.analysis.problemsView.toolWindow

import com.intellij.analysis.problemsView.FileProblem
import com.intellij.analysis.problemsView.Problem
import com.intellij.analysis.problemsView.ProblemsListener
import com.intellij.icons.AllIcons.Toolwindows
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.AppUIUtil
import java.util.concurrent.atomic.AtomicInteger

internal class ProjectErrorsCollector(val project: Project) : ProblemsListener {
  private val fileProblems = mutableMapOf<VirtualFile, MutableSet<FileProblem>>()
  private val otherProblems = mutableSetOf<Problem>()
  private val problemCount = AtomicInteger()

  fun getProblemCount() = problemCount.get()

  fun getFileProblems(file: VirtualFile) = synchronized(fileProblems) {
    fileProblems[file]?.toSet() ?: return emptySet<FileProblem>()
  }

  fun getOtherProblems() = synchronized(otherProblems) {
    otherProblems.toSet()
  }

  override fun problemAppeared(problem: Problem) = notify(problem, when {
    problem.provider.project != project -> State.IGNORED
    problem is FileProblem -> process(problem, true) { add(problem, it) }
    else -> synchronized(otherProblems) { add(problem, otherProblems) }
  })

  override fun problemDisappeared(problem: Problem) = notify(problem, when {
    problem.provider.project != project -> State.IGNORED
    problem is FileProblem -> process(problem, false) { remove(problem, it) }
    else -> synchronized(otherProblems) { remove(problem, otherProblems) }
  })

  override fun problemUpdated(problem: Problem) = notify(problem, when {
    problem.provider.project != project -> State.IGNORED
    problem is FileProblem -> process(problem, false) { update(problem, it) }
    else -> synchronized(otherProblems) { update(problem, otherProblems) }
  })

  private fun process(problem: FileProblem, create: Boolean, function: (MutableSet<FileProblem>) -> State): State {
    synchronized(fileProblems) {
      val file = problem.file
      val set = when (create) {
        true -> fileProblems.computeIfAbsent(file) { mutableSetOf() }
        else -> fileProblems[file] ?: return State.IGNORED
      }
      val state = function(set)
      if (set.isEmpty()) fileProblems.remove(file)
      return state
    }
  }

  private fun notify(problem: Problem, state: State) {
    when (state) {
      State.ADDED -> {
        getProjectErrors()?.addProblem(problem)
        val count = problemCount.incrementAndGet()
        if (count == 1) updateToolWindowIcon()
      }
      State.REMOVED -> {
        getProjectErrors()?.removeProblem(problem)
        val count = problemCount.decrementAndGet()
        if (count == 0) updateToolWindowIcon()
      }
      State.UPDATED -> {
        getProjectErrors()?.updateProblem(problem)
      }
      State.IGNORED -> {
      }
    }
  }

  private fun updateToolWindowIcon() {
    if (!ProblemsView.isProjectErrorsEnabled()) return
    AppUIUtil.invokeLaterIfProjectAlive(project) { ProblemsView.getToolWindow(project)?.setIcon(getToolWindowIcon()) }
  }

  private fun getToolWindowIcon() = when (problemCount.get() == 0) {
    true -> Toolwindows.ToolWindowProblemsEmpty
    else -> Toolwindows.ToolWindowProblems
  }

  private fun getProjectErrors() = ProblemsView.getToolWindow(project)
    ?.contentManagerIfCreated
    ?.contents
    ?.mapNotNull { it.component as? ProjectErrorsPanel }
    ?.firstOrNull()
}

private enum class State { ADDED, REMOVED, UPDATED, IGNORED }

private fun <T> add(element: T, set: MutableSet<T>) = when {
  set.add(element) -> State.ADDED
  else -> update(element, set)
}

private fun <T> remove(element: T, set: MutableSet<T>) = when {
  !set.remove(element) -> State.IGNORED
  else -> State.REMOVED
}

private fun <T> update(element: T, set: MutableSet<T>) = when {
  !set.remove(element) -> State.IGNORED
  !set.add(element) -> State.REMOVED
  else -> State.UPDATED
}
