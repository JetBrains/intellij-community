// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.platform.ide.CoreUiCoroutineScopeHolder
import com.intellij.util.system.OS
import kotlinx.coroutines.launch

internal class ResetWindowsDefenderNotification : AnAction() {
  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = OS.CURRENT == OS.Windows
  }

  override fun actionPerformed(e: AnActionEvent) {
    service<CoreUiCoroutineScopeHolder>().coroutineScope.launch {
      val checker = serviceAsync<WindowsDefenderChecker>()
      checker.ignoreStatusCheck(/*project =*/ null, /*ignore =*/ false)
      e.project?.let { project ->
        checker.ignoreStatusCheck(project, /*ignore =*/ false)
        WindowsDefenderCheckerActivity().execute(project)
      }
    }
  }
}
