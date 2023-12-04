// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.impl

import com.intellij.execution.Executor.ActionWrapper
import com.intellij.execution.ExecutorRegistryImpl
import com.intellij.execution.executors.ExecutorGroup
import com.intellij.execution.executors.RunExecutorSettings
import com.intellij.openapi.actionSystem.*

abstract class DefaultExecutorGroup<Settings : RunExecutorSettings> : ExecutorGroup<Settings>() {

  override fun runnerActionsGroupExecutorActionCustomizer() = ActionWrapper { origin ->
    if (origin is SplitButtonAction && origin.actionGroup is ExecutorRegistryImpl.ExecutorGroupActionGroup) {
      SplitButtonAction(createExecutorGroupWrapper(origin.actionGroup))
    }
    else {
      origin
    }
  }

  protected abstract fun createExecutorGroupWrapper(actionGroup: ActionGroup): ExecutorGroupWrapper

  /**
   * The UI state of the SplitButton placed in the toolbar is a little bit tricky.
   * This class used for fine-grained UI customization.
   *
   * It is possible to completely hide the button from the toolbar if [ExecutorGroupWrapper.groupShouldBeVisible] returns true.
   * However, sometimes it is desired to show disabled (an "empty state") button even if it has no children
   * (just like "Run" button is shown if remote attach configuration is chosen).
   *
   * It is possible to customize disabled presentation in [ExecutorGroupWrapper.updateDisabledActionPresentation],
   * e.g for IntelliJ Profiler ExecutorGroup clocks with green triangle is used as a presentation icon and "Profile" as a presentation text
   */
  protected abstract class ExecutorGroupWrapper(origin: ActionGroup) : ActionGroupWrapper(origin), CompactActionGroup {
    abstract fun groupShouldBeVisible(e: AnActionEvent): Boolean
    abstract fun updateDisabledActionPresentation(eventPresentation: Presentation)

    override fun getChildren(e: AnActionEvent?): Array<AnAction> {
      return super.getChildren(e)
    }

    override fun update(e: AnActionEvent) {
      if (groupShouldBeVisible(e)) {
        updateDisabledActionPresentation(e.presentation)
        super.update(e)

      }
      else {
        e.presentation.isEnabledAndVisible = false
      }
    }
  }
}