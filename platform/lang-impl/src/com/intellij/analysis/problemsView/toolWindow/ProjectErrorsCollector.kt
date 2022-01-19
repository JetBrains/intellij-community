// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.analysis.problemsView.toolWindow

import com.intellij.analysis.problemsView.FileProblem
import com.intellij.analysis.problemsView.HighlightingDuplicate
import com.intellij.analysis.problemsView.Problem
import com.intellij.analysis.problemsView.ProblemsCollector
import com.intellij.analysis.problemsView.ProblemsListener
import com.intellij.analysis.problemsView.ProblemsProvider
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent
import java.util.concurrent.atomic.AtomicInteger

private class ProjectErrorsCollector(val project: Project) : ProblemsCollector {
  private val providerClassFilter = Registry.stringValue("ide.problems.view.provider.class.filter").split(" ,/|").toSet()
  private val fileProblems = mutableMapOf<VirtualFile, MutableSet<FileProblem>>()
  private val otherProblems = mutableSetOf<Problem>()
  private val problemCount = AtomicInteger()

  override fun getProblemCount() = problemCount.get()

  override fun getProblemFiles() = synchronized(fileProblems) {
    fileProblems.keys.toSet()
  }

  override fun getFileProblemCount(file: VirtualFile) = synchronized(fileProblems) {
    fileProblems[file]?.size ?: 0
  }

  override fun getFileProblems(file: VirtualFile) = synchronized(fileProblems) {
    fileProblems[file]?.toSet() ?: emptySet()
  }

  override fun getOtherProblemCount() = synchronized(otherProblems) {
    otherProblems.size
  }

  override fun getOtherProblems() = synchronized(otherProblems) {
    otherProblems.toSet()
  }

  override fun problemAppeared(problem: Problem) {
    val ignored = isIgnored(problem.provider)
    notify(problem, when {
      ignored -> SetUpdateState.IGNORED
      problem is FileProblem -> process(problem, true) { set ->
        when {
          // do not add HighlightingDuplicate if there is any HighlightingProblem
          problem is HighlightingDuplicate && set.any { it is HighlightingProblem } -> SetUpdateState.IGNORED
          else -> SetUpdateState.add(problem, set)
        }
      }
      else -> synchronized(otherProblems) { SetUpdateState.add(problem, otherProblems) }
    })
    if (!ignored && problem is HighlightingProblem) {
      // remove any HighlightingDuplicate if HighlightingProblem is appeared
      synchronized(fileProblems) {
        fileProblems[problem.file]?.filter { it is HighlightingDuplicate }
      }?.forEach { problemDisappeared(it) }
    }
  }

  override fun problemDisappeared(problem: Problem) = notify(problem, when {
    isIgnored(problem.provider) -> SetUpdateState.IGNORED
    problem is FileProblem -> process(problem, false) { SetUpdateState.remove(problem, it) }
    else -> synchronized(otherProblems) { SetUpdateState.remove(problem, otherProblems) }
  })

  override fun problemUpdated(problem: Problem) = notify(problem, when {
    isIgnored(problem.provider) -> SetUpdateState.IGNORED
    problem is FileProblem -> process(problem, false) { SetUpdateState.update(problem, it) }
    else -> synchronized(otherProblems) { SetUpdateState.update(problem, otherProblems) }
  })

  private fun isIgnored(provider: ProblemsProvider) = provider.project != project || providerClassFilter.contains(provider.javaClass.name)

  private fun process(problem: FileProblem, create: Boolean, function: (MutableSet<FileProblem>) -> SetUpdateState): SetUpdateState {
    val file = problem.file
    synchronized(fileProblems) {
      val set = when (create) {
        true -> fileProblems.computeIfAbsent(file) { mutableSetOf() }
        else -> fileProblems[file] ?: return SetUpdateState.IGNORED
      }
      val state = function(set)
      if (set.isEmpty()) fileProblems.remove(file)
      return state
    }
  }

  private fun notify(problem: Problem, state: SetUpdateState, later: Boolean = true) {
    if (state == SetUpdateState.IGNORED || project.isDisposed) return
    if (later && Registry.`is`("ide.problems.view.notify.later")) {
      ApplicationManager.getApplication().invokeLater { notify(problem, state, false) }
      return // notify listeners later on EDT
    }
    when (state) {
      SetUpdateState.ADDED -> {
        project.messageBus.syncPublisher(ProblemsListener.TOPIC).problemAppeared(problem)
        val emptyBefore = problemCount.getAndIncrement() == 0
        if (emptyBefore) ProblemsViewIconUpdater.update(project)
      }
      SetUpdateState.REMOVED -> {
        project.messageBus.syncPublisher(ProblemsListener.TOPIC).problemDisappeared(problem)
        val emptyAfter = problemCount.decrementAndGet() == 0
        if (emptyAfter) ProblemsViewIconUpdater.update(project)
      }
      SetUpdateState.UPDATED -> {
        project.messageBus.syncPublisher(ProblemsListener.TOPIC).problemUpdated(problem)
      }
      SetUpdateState.IGNORED -> {
      }
    }
  }

  private fun onVfsChanges(events: List<VFileEvent>) = events
    .filter { it is VFileDeleteEvent || it is VFileMoveEvent }
    .mapNotNull { it.file }
    .distinct()
    .flatMap { getFileProblems(it) }
    .forEach { problemDisappeared(it) }

  init {
    VirtualFileManager.getInstance().addAsyncFileListener({ onVfsChanges(it);null }, project)
  }
}
