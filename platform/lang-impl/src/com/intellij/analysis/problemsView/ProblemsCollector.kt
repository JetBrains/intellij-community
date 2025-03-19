// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.analysis.problemsView

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

interface ProblemsCollector : ProblemsListener {
  companion object {
    @JvmStatic
    fun getInstance(project: Project): ProblemsCollector = project.getService(ProblemsCollector::class.java)!!
  }

  fun getProblemCount(): Int
  fun getProblemFiles(): Collection<VirtualFile>

  fun getFileProblemCount(file: VirtualFile): Int
  fun getFileProblems(file: VirtualFile): Collection<Problem>

  fun getOtherProblemCount(): Int
  fun getOtherProblems(): Collection<Problem>
}
