// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.problems

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.daemon.problems.SnapshotUpdater.Companion.api
import com.intellij.codeInsight.daemon.problems.ui.ProjectProblemsView
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent
import com.intellij.pom.Navigatable
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope

internal data class Change(val prevMember: Member?, val curMember: Member?, val containingFile: PsiFile)
internal data class Problem(val file: VirtualFile, val message: String?, val place: Navigatable)

@Service
class ProblemAnalyzer(private val project: Project) : DaemonCodeAnalyzer.DaemonListener, BulkFileListener, FileEditorManagerListener {

  private val psiManager = PsiManager.getInstance(project)
  private val usageSink = UsageSink(project)

  init {
    DumbService.getInstance(project).runWhenSmart {
      val connection = project.messageBus.connect()
      connection.subscribe(DaemonCodeAnalyzer.DAEMON_EVENT_TOPIC, this)
      connection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, this)
      connection.subscribe(VirtualFileManager.VFS_CHANGES, this)
    }
  }

  override fun daemonFinished(fileEditors: MutableCollection<FileEditor>) {
    fileEditors.mapNotNull { it.file }.forEach { analyzeFile(it) }
  }

  override fun selectionChanged(event: FileEditorManagerEvent) {
    val file = event.newFile ?: return
    val psiFile = psiManager.findFile(file) as? PsiClassOwner ?: return
    val scope = psiFile.useScope as? GlobalSearchScope ?: return
    DumbService.getInstance(project).smartInvokeLater { reportBrokenUsages(psiFile, scope, false) }
  }

  override fun before(events: MutableList<out VFileEvent>) {
    val problemsView = ProjectProblemsView.SERVICE.getInstance(project)
    for (event in events) {
      if (event !is VFileDeleteEvent && event !is VFileMoveEvent) continue
      val file = event.file ?: continue
      var psiFile = psiManager.findFile(file) as? PsiClassOwner ?: continue
      problemsView.removeProblems(file)
      val scope = psiFile.useScope as? GlobalSearchScope ?: continue
      psiFile = psiFile.copy() as? PsiClassOwner ?: continue
      DumbService.getInstance(project).smartInvokeLater { reportBrokenUsages(psiFile, scope, true) }
    }
  }

  override fun after(events: MutableList<out VFileEvent>) {
    for (event in events) {
      if (event !is VFileCreateEvent && event !is VFileMoveEvent) continue
      val file = event.file ?: continue
      val psiFile = psiManager.findFile(file) as? PsiClassOwner ?: continue
      val scope = psiFile.useScope as? GlobalSearchScope ?: continue
      DumbService.getInstance(project).smartInvokeLater { reportBrokenUsages(psiFile, scope, false) }
    }
  }

  private fun reportBrokenUsages(psiFile: PsiClassOwner, scope: GlobalSearchScope, isRemoved: Boolean) {
    if (!psiFile.isValid && !isRemoved) return
    val changes = api(psiFile).mapNotNullTo(mutableSetOf()) { memberChange(it, psiFile, scope, isRemoved) }
    reportProblems(changes)
  }

  private fun memberChange(psiMember: PsiMember, containingFile: PsiFile, scope: GlobalSearchScope, isRemoved: Boolean): Change? {
    val member = Member.create(psiMember, scope) ?: return null
    return if (isRemoved) Change(member, null, containingFile) else Change(null, member, containingFile)
  }

  private fun analyzeFile(file: VirtualFile) {
    if (!file.isValid) return
    val psiFile = psiManager.findFile(file) as? PsiClassOwner ?: return
    for (psiClass in psiFile.classes) {
      val changes = SnapshotUpdater.update(psiClass)
      if (changes.isEmpty()) continue
      reportProblems(changes)
    }
  }

  private fun reportProblems(changes: Set<Change>) {
    val problemsView = ProjectProblemsView.SERVICE.getInstance(project)
    problemsView.executor().execute {
      val problems = ReadAction.nonBlocking<List<Problem>> {
        val problems = mutableListOf<Problem>()
        for ((prevMember, curMember, containingFile) in changes) {
          if (project.isDisposed) return@nonBlocking emptyList()
          val problemsAfterChange = usageSink.checkUsages(prevMember, curMember, containingFile)
          if (problemsAfterChange != null) problems.addAll(problemsAfterChange)
        }
        return@nonBlocking problems
      }.executeSynchronously()
      if (problems.isEmpty()) return@execute

      ApplicationManager.getApplication().invokeLater {
        updateProblems(problems, problemsView)
      }
    }
  }

  private fun updateProblems(newProblems: List<Problem>, problemsView: ProjectProblemsView) {
    val problemsByFile: Map<VirtualFile, MutableMap<Navigatable, String?>> = newProblems.groupingBy { it.file }.aggregate { _, acc, el, _ ->
      val newAcc = acc ?: mutableMapOf()
      newAcc[el.place] = el.message
      newAcc
    }
    for ((file, updatedProblems) in problemsByFile) {
      problemsView.getProblems(file).forEach { if (it is PsiElement && !it.isValid) problemsView.removeProblems(file, it) }
      for ((place, message) in updatedProblems) {
        problemsView.removeProblems(file, place)
        if (message != null) problemsView.addProblem(file, message, place)
      }
    }
  }

  class MyStartupActivity : StartupActivity {
    override fun runActivity(project: Project) {
      if (!Registry.`is`("project.problems.view")) return
      project.service<ProblemAnalyzer>()
    }
  }
}