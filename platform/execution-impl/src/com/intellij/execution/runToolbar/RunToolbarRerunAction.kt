// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.runToolbar

import com.intellij.execution.ExecutionBundle
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.FakeRerunAction
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware

open class RunToolbarRerunAction : FakeRerunAction(), RTBarAction, DumbAware {
  override fun getRightSideType(): RTBarAction.Type = RTBarAction.Type.RIGHT_FLEXIBLE

  override fun update(event: AnActionEvent) {
    super.update(event)
    event.presentation.text = ExecutionBundle.message("run.dashboard.rerun.action.name")
    event.presentation.isEnabledAndVisible =
      event.presentation.isEnabled
      && event.presentation.isVisible
      && getDescriptor(event) != null
      && event.presentation.isVisible
      && if(event.isItRunToolbarMainSlot() && !event.isOpened()) event.project?.let {
        RunToolbarSlotManager.getInstance(it).getState().isSingleMain()
      } ?: false else true
  }

  override fun actionPerformed(event: AnActionEvent) {
    event.environment()?.let {
      event.addWaitingForAProcess(it.executor.id)
      super.actionPerformed(event)
    }

  }

  override fun getEnvironment(event: AnActionEvent): ExecutionEnvironment? {
    return event.environment()
  }

  override fun getDescriptor(event: AnActionEvent): RunContentDescriptor? {
    return getEnvironment(event)?.contentToReuse
  }
}
