// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.util.SystemInfo
import kotlinx.coroutines.launch

private class ResetWindowsDefenderNotification : AnAction() {
  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = SystemInfo.isWindows
  }

  override fun actionPerformed(e: AnActionEvent) {
    val checker = WindowsDefenderChecker.getInstance()
    checker.ignoreStatusCheck(null, false)
    val project = e.project ?: return
    checker.ignoreStatusCheck(project, false)
    @Suppress("DEPRECATION")
    project.coroutineScope.launch {
      WindowsDefenderCheckerActivity().execute(project)
    }
  }
}
