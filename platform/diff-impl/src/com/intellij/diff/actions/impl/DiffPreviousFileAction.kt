// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.actions.impl

import com.intellij.diff.tools.util.DiffDataKeys
import com.intellij.diff.util.DiffUtil
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware

internal class DiffPreviousFileAction : AnAction(), DumbAware {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    if (DiffUtil.isFromShortcut(e)) {
      e.presentation.setEnabledAndVisible(true)
      return
    }

    val iterable = e.getData(DiffDataKeys.PREV_NEXT_FILE_ITERABLE)
    if (iterable == null) {
      e.presentation.setEnabledAndVisible(false)
      return
    }

    e.presentation.setVisible(true)
    e.presentation.setEnabled(iterable.canGoPrev(true))
  }

  override fun actionPerformed(e: AnActionEvent) {
    val iterable = e.getData(DiffDataKeys.PREV_NEXT_FILE_ITERABLE)
    if (iterable == null || !iterable.canGoPrev(false)) return
    iterable.goPrev(false)
  }

  companion object {
    const val ID: String = "Diff.PrevChange"
  }
}
