// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.runToolbar

import com.intellij.execution.ExecutorRegistry
import com.intellij.execution.runToolbar.data.RWActiveListener
import com.intellij.openapi.actionSystem.ex.ActionManagerEx
import com.intellij.openapi.project.Project

internal class RunToolbarShortcutHelper(val project: Project) : RWActiveListener {
  override fun enabled() {
    val actionManager = ActionManagerEx.getInstanceEx()
    for (process in RunToolbarProcess.getProcesses()) {
      ExecutorRegistry.getInstance().getExecutorById(process.executorId)?.let { executor ->
        actionManager.getAction(executor.id)?.let {
          actionManager.getAction(process.getMainActionId())?.let {
            actionManager.bindShortcuts(executor.id, process.getMainActionId())
          }
        }
      }
    }
  }

  override fun disabled() {
    val actionManager = ActionManagerEx.getInstanceEx()
    for (process in RunToolbarProcess.getProcesses()) {
      ExecutorRegistry.getInstance().getExecutorById(process.executorId)?.let {
        actionManager.getAction(process.getMainActionId())?.let {
          actionManager.unbindShortcuts(process.getMainActionId())
        }
      }
    }
  }
}