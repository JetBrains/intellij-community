package com.intellij.analysis.problemsView.toolWindow

import com.intellij.analysis.problemsView.ProblemsProvider
import com.intellij.openapi.project.Project
import com.intellij.problems.ProblemListener

interface HighlightingErrorsProviderBase: ProblemsProvider, ProblemListener {
  companion object {
    @JvmStatic
    fun getInstance(project: Project): HighlightingErrorsProviderBase = project.getService(HighlightingErrorsProviderBase::class.java)!!
  }
}