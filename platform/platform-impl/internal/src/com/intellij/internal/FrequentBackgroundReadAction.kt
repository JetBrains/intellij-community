// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ex.CheckboxAction
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.ui.Messages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import kotlin.coroutines.cancellation.CancellationException

@ApiStatus.Internal
class FrequentBackgroundReadAction: CheckboxAction() {
  companion object {
    @Volatile
    private var runningJob: Job? = null

    @Volatile
    private var sleepDurationMs: Long = 10
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  override fun isSelected(e: AnActionEvent): Boolean {
    return runningJob?.isActive == true
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    if (state) {
      // Show input dialog to get sleep duration
      val input = Messages.showInputDialog(
        e.project,
        "Enter sleep duration in milliseconds:",
        "Frequent Background Read Action",
        null,
        sleepDurationMs.toString(),
        null
      )

      if (input.isNullOrBlank()) {
        return // User cancelled
      }

      val duration = input.toLongOrNull()
      if (duration == null || duration < 0) {
        Messages.showErrorDialog(
          e.project,
          "Invalid duration. Please enter a positive number.",
          "Invalid Input"
        )
        return
      }

      sleepDurationMs = duration

      // Toggle ON: Start repeated background read actions
      runningJob = e.coroutineScope.launch(Dispatchers.Default) {
        try {
          while (isActive) {
            runReadAction {
              Thread.sleep(sleepDurationMs)
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
