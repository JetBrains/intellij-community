// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.analysis.problemsView.toolWindow

import com.intellij.analysis.problemsView.HighlightingDuplicate
import com.intellij.analysis.problemsView.ProblemsCollector
import com.intellij.analysis.problemsView.ProblemsProvider
import com.intellij.codeInsight.problems.WolfTheProblemSolverImpl
import com.intellij.lang.annotation.HighlightSeverity.ERROR
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.problems.ProblemListener
import com.intellij.problems.WolfTheProblemSolver

internal class HighlightingErrorsProvider(override val project: Project) : ProblemsProvider, ProblemListener {
  companion object {
    @JvmStatic
    fun getInstance(project: Project) = project.getService(HighlightingErrorsProvider::class.java)!!
  }

  private val watchers = mutableMapOf<VirtualFile, HighlightingWatcher>()

  init {
    project.messageBus.connect(this).subscribe(ProblemListener.TOPIC, this)
    (WolfTheProblemSolver.getInstance(project) as? WolfTheProblemSolverImpl)?.processProblemFiles {
      problemsAppeared(it)
      true
    }
  }

  internal fun isFileWatched(file: VirtualFile) = synchronized(watchers) { watchers.containsKey(file) }

  override fun problemsAppeared(file: VirtualFile) {
    val added = synchronized(watchers) {
      val size = watchers.size
      watchers.computeIfAbsent(file) { file ->
        HighlightingWatcher(this, ProblemsCollector.getInstance(project), file, ERROR.myVal).also { watcher ->
          Disposer.register(this, watcher)
        }
      }
      size < watchers.size
    }
    if (added) {
      val collector = ProblemsCollector.getInstance(project)
      collector.getFileProblems(file)
        .filter { it is HighlightingDuplicate }
        .forEach { collector.problemDisappeared(it) }
    }
  }

  override fun problemsDisappeared(file: VirtualFile) {
    val watcher = synchronized(watchers) { watchers.remove(file) } ?: return
    Disposer.dispose(watcher) // removes a markup model listener
  }
}
