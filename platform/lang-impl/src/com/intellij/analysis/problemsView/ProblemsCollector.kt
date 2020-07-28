// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.analysis.problemsView

import com.intellij.openapi.vfs.VirtualFile

interface ProblemsCollector : ProblemsListener {
  fun getProblemCount(): Int

  fun getFileProblemCount(file: VirtualFile): Int
  fun getFileProblems(file: VirtualFile): Collection<Problem>

  fun getOtherProblemCount(): Int
  fun getOtherProblems(): Collection<Problem>
}
