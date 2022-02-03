// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.analysis.problemsView.toolWindow

import com.intellij.analysis.problemsView.ProblemsCollector
import com.intellij.icons.AllIcons.Toolwindows
import com.intellij.openapi.project.Project
import com.intellij.ui.BadgeIcon
import com.intellij.ui.ExperimentalUI
import com.intellij.util.SingleAlarm
import com.intellij.util.ui.JBUI

internal class ProblemsViewIconUpdater(private val project: Project) {
  private val alarm = SingleAlarm({ ProblemsView.getToolWindow(project)?.setIcon(getIcon()) }, 50, project)
  private val empty = Toolwindows.ToolWindowProblemsEmpty
  private val badge by lazy { BadgeIcon(empty, JBUI.CurrentTheme.IconBadge.ERROR) }

  private fun getIcon() = getIcon(ProblemsCollector.getInstance(project).getProblemCount() == 0)
  private fun getIcon(noErrors: Boolean) = when {
    noErrors -> empty
    ExperimentalUI.isNewUI() -> badge
    else -> Toolwindows.ToolWindowProblems
  }

  companion object {
    @JvmStatic
    fun update(project: Project) = project.getService(ProblemsViewIconUpdater::class.java)?.alarm?.cancelAndRequest()
  }
}
