// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAwareAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

internal class IoFloodingAction : DumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    if (value) {
      value = false
    }
    else {
      value = true
      val scope = service<Holder>().scope
      repeat(100) {
        scope.launch(Dispatchers.IO) {
          while (value) {
            Thread.sleep(50)
          }
        }
      }
    }
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    e.presentation.text = if (value)
      InternalActionsBundle.message("stop.IoFlooding.text")
    else
      InternalActionsBundle.message("start.IoFlooding.text")
  }
}

@Volatile
private var value: Boolean = false

@Service(Service.Level.APP)
private class Holder(val scope: CoroutineScope)