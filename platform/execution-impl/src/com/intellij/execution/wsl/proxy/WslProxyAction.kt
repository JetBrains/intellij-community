// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.wsl.proxy

import com.intellij.execution.wsl.WslDistributionManager
import com.intellij.execution.wsl.WslProxy
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.Disposer
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.ui.components.JBTextField
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.swing.JComponent

@Suppress("HardCodedStringLiteral") // This is a test, internal only action
private class WslProxyAction : DumbAwareAction("WslProxyAction") {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  @RequiresEdt
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: ProjectManager.getInstance().defaultProject
    val wsls = runWithModalProgressBlocking(project, "Getting list of WSL") {
      withContext(Dispatchers.IO) {
        WslDistributionManager.getInstance().installedDistributions
      }
    }
    val model = WslProxyActionModel(wsls)
    if (!WslProxyActionUi(project, model).showAndGet()) {
      return
    }
    val proxy = WslProxy(model.selectedDistribution!!, model.port)
    val port = proxy.wslIngressPort

    object : DialogWrapper(project) {
      init {
        init()
      }

      override fun createCenterPanel(): JComponent = JBTextField("Port $port. On Linux run iperf3 -c 127.0.0.1 --port $port")
    }.showAndGet()
    Disposer.dispose(proxy)
  }
}