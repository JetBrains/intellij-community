// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.analysis.problemsView.toolWindow

import com.intellij.analysis.problemsView.ProblemsCollector
import com.intellij.codeInsight.daemon.impl.WolfTheProblemSolverImpl
import com.intellij.lang.annotation.HighlightSeverity.ERROR
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.problems.ProblemListener
import com.intellij.problems.WolfTheProblemSolver

open class HighlightingErrorsProvider(final override val project: Project) : HighlightingErrorsProviderBase {
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

  override fun problemsAppeared(file: VirtualFile) {
    if (!file.isValid || FileTypeRegistry.getInstance().isFileIgnored(file)) return
    if (project.isDisposed || ProjectFileIndex.getInstance(project).isExcluded(file)) return
    synchronized(watchers) {
      watchers.computeIfAbsent(file) { file ->
        HighlightingWatcher(this, ProblemsCollector.getInstance(project), file, ERROR.myVal).also { watcher ->
          Disposer.register(this, watcher)
        }
      }
    }
  }

  override fun problemsDisappeared(file: VirtualFile) {
    val watcher = synchronized(watchers) { watchers.remove(file) } ?: return
    Disposer.dispose(watcher) // removes a markup model listener
  }
}
