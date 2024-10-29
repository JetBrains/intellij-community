// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.analysis.problemsView.toolWindow

import com.intellij.analysis.problemsView.ProblemsCollector
import com.intellij.icons.AllIcons.Toolwindows
import com.intellij.openapi.project.Project
import com.intellij.ui.BadgeIcon
import com.intellij.ui.ExperimentalUI
import com.intellij.util.SingleAlarm
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.CoroutineScope
import javax.swing.Icon

open class ProblemsViewIconUpdater(project: Project, coroutineScope: CoroutineScope) {
  private val alarm = SingleAlarm.singleEdtAlarm(
    delay = 50,
    coroutineScope = coroutineScope,
    task = { ProblemsView.getToolWindow(project)?.setIcon(getIcon(getProblemCount(project))) },
  )
  private val empty = Toolwindows.ToolWindowProblemsEmpty
  private val badge by lazy { BadgeIcon(empty, JBUI.CurrentTheme.IconBadge.ERROR) }

  protected open fun getProblemCount(project: Project): Int = ProblemsCollector.getInstance(project).getProblemCount()

  protected open fun getIcon(problemCount: Int): Icon = when {
    problemCount == 0 -> empty
    ExperimentalUI.isNewUI() -> badge
    else -> Toolwindows.ToolWindowProblems
  }

  companion object {
    @JvmStatic
    fun update(project: Project):Unit? {
      return project.getService(ProblemsViewIconUpdater::class.java)?.alarm?.cancelAndRequest()
    }
  }
}
