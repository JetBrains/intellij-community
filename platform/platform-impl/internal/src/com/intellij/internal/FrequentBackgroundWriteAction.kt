// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ex.CheckboxAction
import com.intellij.openapi.application.backgroundWriteAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import kotlin.coroutines.cancellation.CancellationException

@ApiStatus.Internal
class FrequentBackgroundWriteAction: CheckboxAction() {
  companion object {
    @Volatile
    private var runningJob: Job? = null
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  override fun isSelected(e: AnActionEvent): Boolean {
    return runningJob?.isActive == true
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    if (state) {
      // Toggle ON: Start repeated background write actions
      runningJob = e.coroutineScope.launch(Dispatchers.Default) {
        try {
          while (coroutineContext.isActive) {
            backgroundWriteAction {
              Thread.sleep(1)
            }
            delay(1)
          }
        } catch (e: CancellationException) {
          // Expected on toggle OFF
          throw e
        }
      }
    } else {
      // Toggle OFF: Cancel the running job
      runningJob?.cancel()
      runningJob = null
    }
  }
}
