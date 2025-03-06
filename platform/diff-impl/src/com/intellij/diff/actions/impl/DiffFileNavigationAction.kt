// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.actions.impl

import com.intellij.diff.actions.impl.DiffFileNavigationAction.Companion.isAvailable
import com.intellij.diff.tools.util.DiffDataKeys
import com.intellij.diff.tools.util.PrevNextFileIterable
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.DumbAware

internal abstract class DiffFileNavigationAction : AnAction(), DumbAware {
  final override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  /**
   * @see [isAvailable]
   */
  final override fun update(e: AnActionEvent) {
    val iterable = e.getData(DiffDataKeys.PREV_NEXT_FILE_ITERABLE)
    val isAvailable = isAvailable(iterable)
    if (!isAvailable) {
      e.presentation.setEnabledAndVisible(false)
      return
    }

    e.presentation.setVisible(true)
    e.presentation.setEnabled(iterable != null && iterable.canNavigate(true))
  }

  protected abstract fun PrevNextFileIterable.canNavigate(fastCheck: Boolean): Boolean

  final override fun actionPerformed(e: AnActionEvent) {
    val iterable = e.getData(DiffDataKeys.PREV_NEXT_FILE_ITERABLE)
    if (iterable == null || !iterable.canNavigate(false)) return
    iterable.navigate()
  }

  protected abstract fun PrevNextFileIterable.navigate()

  companion object {
    fun isAvailable(dataContext: DataContext): Boolean {
      val iterable = dataContext.getData(DiffDataKeys.PREV_NEXT_FILE_ITERABLE)
      return isAvailable(iterable)
    }

    private fun isAvailable(iterable: PrevNextFileIterable?): Boolean = iterable != null
  }
}