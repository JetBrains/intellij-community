// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory
import com.intellij.openapi.project.DumbAware
import com.intellij.ui.navigation.History

internal class BackAction : AnAction(), DumbAware {
  init {
    isEnabledInModalContext = true
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project
    val history = e.getData(History.KEY)

    if (history != null) {
      history.back()
    }
    else if (project != null) {
      IdeDocumentHistory.getInstance(project).back()
    }
  }

  override fun update(e: AnActionEvent) {
    val project = e.project
    val history = e.getData(History.KEY)

    e.presentation.isEnabled = when {
      history != null -> history.canGoBack()
      project != null && !project.isDisposed -> {
        val isModalContext = e.getData(PlatformCoreDataKeys.IS_MODAL_CONTEXT) == true
        !isModalContext && IdeDocumentHistory.getInstance(project).isBackAvailable
      }
      else -> false
    }
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}