// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.analysis.problemsView.toolWindow

import com.intellij.analysis.problemsView.ProblemsCollector
import com.intellij.icons.AllIcons.Toolwindows
import com.intellij.openapi.project.Project
import com.intellij.ui.BadgeIcon
import com.intellij.ui.ExperimentalUI
import com.intellij.util.SingleAlarm
import com.intellij.util.ui.JBUI

open class ProblemsViewIconUpdater(project: Project) {
  private val alarm = SingleAlarm({ ProblemsView.getToolWindow(project)?.setIcon(getIcon(getProblemCount(project))) }, 50, project)
  private val empty = Toolwindows.ToolWindowProblemsEmpty
  private val badge by lazy { BadgeIcon(empty, JBUI.CurrentTheme.IconBadge.ERROR) }

  protected open fun getProblemCount(project: Project) = ProblemsCollector.getInstance(project).getProblemCount()

  protected open fun getIcon(problemCount: Int) = when {
    problemCount == 0 -> empty
    ExperimentalUI.isNewUI() -> badge
    else -> Toolwindows.ToolWindowProblems
  }

  companion object {
    @JvmStatic
    fun update(project: Project) = project.getService(ProblemsViewIconUpdater::class.java)?.alarm?.cancelAndRequest()
  }
}
