// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.analysis.problemsView

import com.intellij.openapi.project.Project

interface ProblemsListener {
  companion object {
    @JvmStatic
    fun getInstance(project: Project) = project.getService(ProblemsListener::class.java)!!
  }

  fun problemAppeared(problem: Problem)
  fun problemDisappeared(problem: Problem)
  fun problemUpdated(problem: Problem)
}
