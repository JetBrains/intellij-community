// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.execution.serviceView.frontend

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.EDT
import com.intellij.openapi.progress.currentThreadCoroutineScope
import com.intellij.openapi.project.DumbAwareToggleAction
import com.intellij.platform.execution.serviceView.isNewFrontendServiceViewEnabled
import com.intellij.platform.execution.serviceView.setServiceViewImplementationForNextIdeRun
import com.intellij.platform.execution.serviceView.splitApi.ServiceViewRpc
import com.intellij.platform.ide.productMode.IdeProductMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class SwitchServiceViewImplementationAction : DumbAwareToggleAction() {

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = IdeProductMode.isFrontend
  }

  override fun isSelected(e: AnActionEvent): Boolean {
    return isNewFrontendServiceViewEnabled()
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    currentThreadCoroutineScope().launch {
      setServiceViewImplementationForNextIdeRun(state)
      ServiceViewRpc.getInstance().changeServiceViewImplementationForNextIdeRun(state)
      withContext(Dispatchers.EDT) {
        ActionManager.getInstance().getAction("RestartIde").actionPerformed(e)
      }
    }
  }
}