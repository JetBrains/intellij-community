// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.actions.impl

import com.intellij.diff.tools.util.DiffDataKeys
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware

internal open class DiffNextDifferenceAction : AnAction(), DumbAware {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  override fun update(e: AnActionEvent) {
    val iterable = e.getData(DiffDataKeys.PREV_NEXT_DIFFERENCE_ITERABLE)
    if (iterable == null) {
      e.presentation.setEnabledAndVisible(false)
      return
    }
    else if (iterable.canGoNext()) {
      e.presentation.setEnabledAndVisible(true)
      return
    }

    val fileIterable = e.getData(DiffDataKeys.PREV_NEXT_FILE_ITERABLE)
    val crossFileIterable = e.getData(DiffDataKeys.CROSS_FILE_PREV_NEXT_DIFFERENCE_ITERABLE)
    if (fileIterable != null && crossFileIterable != null && fileIterable.canGoNext(true)) {
      e.presentation.setEnabled(true)
      return
    }

    e.presentation.setEnabled(false)
  }

  override fun actionPerformed(e: AnActionEvent) {
    val iterable = e.getData(DiffDataKeys.PREV_NEXT_DIFFERENCE_ITERABLE)
    val crossFileIterable = e.getData(DiffDataKeys.CROSS_FILE_PREV_NEXT_DIFFERENCE_ITERABLE)

    if (iterable != null && iterable.canGoNext()) {
      iterable.goNext()
      crossFileIterable?.reset()
      return
    }

    val fileIterable = e.getData(DiffDataKeys.PREV_NEXT_FILE_ITERABLE)
    if (crossFileIterable == null || fileIterable == null || !fileIterable.canGoNext(false)) {
      return
    }

    if (crossFileIterable.canGoNextNow()) {
      fileIterable.goNext(true)
    }
    else {
      crossFileIterable.prepareGoNext(e.dataContext)
    }
  }

  companion object {
    const val ID = "NextDiff"
  }
}
