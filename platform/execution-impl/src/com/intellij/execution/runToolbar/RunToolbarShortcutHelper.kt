// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.runToolbar

import com.intellij.execution.ExecutorRegistry
import com.intellij.execution.runToolbar.data.RWActiveListener
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.project.Project

class RunToolbarShortcutHelper(val project: Project) : RWActiveListener {

  override fun enabled() {
    val actionManager = ActionManager.getInstance()
    RunToolbarProcess.getProcesses().forEach { process ->
      ExecutorRegistry.getInstance().getExecutorById(process.executorId)?.let { executor ->
        actionManager.getAction(executor.id)?.let {
          actionManager.getAction(process.getMainActionId())?.let {
            KeymapManager.getInstance().bindShortcuts(executor.id, process.getMainActionId())
          }
        }
      }
    }
  }

  override fun disabled() {
    val actionManager = ActionManager.getInstance()
    RunToolbarProcess.getProcesses().forEach { process ->
      ExecutorRegistry.getInstance().getExecutorById(process.executorId)?.let { executor ->
        actionManager.getAction(process.getMainActionId())?.let {
          KeymapManager.getInstance().unbindShortcuts(process.getMainActionId())
        }
      }
    }
  }
}