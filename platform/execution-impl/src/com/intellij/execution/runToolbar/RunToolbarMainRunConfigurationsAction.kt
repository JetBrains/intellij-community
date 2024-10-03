// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.runToolbar

import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.openapi.actionSystem.AnActionEvent
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class RunToolbarMainRunConfigurationsAction : RunToolbarRunConfigurationsAction() {

  override fun checkMainSlotVisibility(state: RunToolbarMainSlotState): Boolean {
    return state == RunToolbarMainSlotState.CONFIGURATION
  }

  override fun getSelectedConfiguration(e: AnActionEvent): RunnerAndConfigurationSettings? {
    return e.project?.let {
      val manager = RunToolbarSlotManager.getInstance(it)
      manager.mainSlotData.configuration
    } ?: super.getSelectedConfiguration(e)
  }
}