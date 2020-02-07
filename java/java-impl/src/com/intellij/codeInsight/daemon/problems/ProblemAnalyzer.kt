// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.problems

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiClassOwner
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager

internal data class Change(val prevMember: Member?, val curMember: Member?, val containingFile: PsiFile)

@Service
class ProblemAnalyzer(private val project: Project) : DaemonCodeAnalyzer.DaemonListener {

  val psiManager = PsiManager.getInstance(project)

  init {
    DumbService.getInstance(project).runWhenSmart {
      project.messageBus.connect().subscribe(DaemonCodeAnalyzer.DAEMON_EVENT_TOPIC, this)
    }
  }

  class MyStartupActivity : StartupActivity {
    override fun runActivity(project: Project) {
      if (!Registry.`is`("project.problems.view") && !ApplicationManager.getApplication().isUnitTestMode) return
      project.service<ProblemAnalyzer>()
    }
  }

  override fun daemonFinished(fileEditors: MutableCollection<FileEditor>) {
    fileEditors.mapNotNull { it.file }.forEach { analyzeFile(it) }
  }

  private fun analyzeFile(file: VirtualFile) {
    if (!file.isValid) return
    val psiFile = psiManager.findFile(file) as? PsiClassOwner ?: return
    for (psiClass in psiFile.classes) {
      SnapshotUpdater.update(psiClass)
    }
  }
}