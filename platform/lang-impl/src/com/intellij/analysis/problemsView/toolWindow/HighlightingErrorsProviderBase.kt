// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.analysis.problemsView.toolWindow

import com.intellij.analysis.problemsView.ProblemsProvider
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.problems.ProblemListener

interface HighlightingErrorsProviderBase: ProblemsProvider, ProblemListener {
  companion object {
    fun getInstance(project: Project): HighlightingErrorsProviderBase = project.service()
  }
}