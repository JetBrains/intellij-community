// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.analysis.problemsView.toolWindow

import com.intellij.analysis.problemsView.Problem
import com.intellij.analysis.problemsView.ProblemsCollector
import com.intellij.analysis.problemsView.ProblemsListener
import com.intellij.openapi.vfs.VirtualFile

internal class CollectorBasedRoot(panel: ProblemsViewPanel, val collector: ProblemsCollector) : Root(panel) {
  internal constructor(panel: ProblemsViewPanel) : this(panel, ProblemsCollector.getInstance(panel.project)) {
    panel.project.messageBus.connect(this).subscribe(ProblemsListener.TOPIC, this)
  }

  override fun getProblemCount(): Int = collector.getProblemCount()
  override fun getProblemFiles(): Collection<VirtualFile> = collector.getProblemFiles()

  override fun getFileProblemCount(file: VirtualFile): Int = collector.getFileProblemCount(file)
  override fun getFileProblems(file: VirtualFile): Collection<Problem> = collector.getFileProblems(file)

  override fun getOtherProblemCount(): Int = collector.getOtherProblemCount()
  override fun getOtherProblems(): Collection<Problem> = collector.getOtherProblems()
}
