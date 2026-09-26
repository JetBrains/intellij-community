// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.actions

import com.intellij.execution.Executor
import com.intellij.execution.ExecutorRegistry
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.ide.ui.IdeUiService
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.impl.Utils.computeWithProgressIcon
import com.intellij.openapi.application.readAction
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.wm.ToolWindowId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

open class ChooseRunConfigurationPopupAction : AnAction(), DumbAware {
  override fun actionPerformed(e: AnActionEvent) {
    val popup = ChooseRunConfigurationPopup(e.project!!, adKey, defaultExecutor!!, alternativeExecutor)
    val asyncDataContext = IdeUiService.getInstance().createAsyncDataContext(e.dataContext)
    val step = computeWithProgressIcon(e.dataContext, e.place) {
      withContext(Dispatchers.Default) {
        readAction { popup.buildStep(asyncDataContext) }
      }
    }
    popup.show(step)
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = e.getData(CommonDataKeys.PROJECT)?.isDisposed == false && defaultExecutor != null
  }

  protected open val defaultExecutor: Executor?
    get() = DefaultRunExecutor.getRunExecutorInstance()

  protected open val alternativeExecutor: Executor?
    get() = ExecutorRegistry.getInstance().getExecutorById(ToolWindowId.DEBUG)

  protected open val adKey = "run.configuration.alternate.action.ad"
}
