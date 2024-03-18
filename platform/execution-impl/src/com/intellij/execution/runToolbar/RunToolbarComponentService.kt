// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.runToolbar

import com.intellij.execution.ExecutionListener
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.ide.ui.ToolbarSettings
import com.intellij.openapi.actionSystem.ex.ActionRuntimeRegistrar
import com.intellij.openapi.actionSystem.impl.ActionConfigurationCustomizer
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.ui.ExperimentalUI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private val LOG = logger<RunToolbarComponentService>()

@Service(Service.Level.PROJECT)
private class RunToolbarComponentService(private val project: Project, private val coroutineScope: CoroutineScope) {
  private val extraSlots: RunToolbarSlotManager
    get() = RunToolbarSlotManager.getInstance(project)

  class MyExecutionListener(private val project: Project) : ExecutionListener {
    override fun processNotStarted(executorId: String, env: ExecutionEnvironment) {
      handle {
        it.processNotStarted(env)
      }
    }

    override fun processStarted(executorId: String, env: ExecutionEnvironment, handler: ProcessHandler) {
      handle {
        it.start(env)
      }
    }

    override fun processTerminating(executorId: String, env: ExecutionEnvironment, handler: ProcessHandler) {
      handle {
        it.terminating(env)
      }
    }

    override fun processTerminated(executorId: String, env: ExecutionEnvironment, handler: ProcessHandler, exitCode: Int) {
      handle {
        it.terminated(env)
      }
    }

    private inline fun handle(crossinline task: (RunToolbarComponentService) -> Unit) {
      if (!ToolbarSettings.getInstance().isAvailable) {
        return
      }

      val toolbarComponentService = project.service<RunToolbarComponentService>()
      toolbarComponentService.coroutineScope.launch(Dispatchers.EDT) {
        task(toolbarComponentService)
      }
    }
  }

  private fun start(env: ExecutionEnvironment) {
    if (isRelevant(env)) {
      val extraSlots = extraSlots
      if (RunToolbarProcess.logNeeded) {
        LOG.info("new active: ${env.executor.id} ${env}, slot manager ${if (extraSlots.active) "ENABLED" else "DISABLED"} RunToolbar")
      }
      if (extraSlots.active) {
        extraSlots.processStarted(env)
      }
    }
  }

  private fun processNotStarted(env: ExecutionEnvironment) {
    if (env.getRunToolbarProcess() != null) {
      if (RunToolbarProcess.logNeeded) {
        LOG.info("Not started: ${env.executor.id} ${env} RunToolbar")
      }
      if (extraSlots.active) {
        extraSlots.processNotStarted(env)
      }
    }
  }

  private fun terminated(env: ExecutionEnvironment) {
    val extraSlots = extraSlots
    if (RunToolbarProcess.logNeeded) {
        LOG.info("removed: ${env.executor.id} ${env}, slot manager ${if (extraSlots.active) "ENABLED" else "DISABLED"} RunToolbar")
      }
      if (extraSlots.active) {
        extraSlots.processTerminated(env.executionId)
      }
  }

  private fun terminating(env: ExecutionEnvironment) {
    val extraSlots = extraSlots
    if (RunToolbarProcess.logNeeded) {
      LOG.info("terminating: ${env.executor.id} ${env}, slot manager ${if (extraSlots.active) "ENABLED" else "DISABLED"} RunToolbar")
    }
    if (extraSlots.active) {
      if (isRelevant(env)) {
        extraSlots.processTerminating(env)
      }
      else {
        extraSlots.processTerminated(env.executionId)
      }
    }
  }

  private fun isRelevant(environment: ExecutionEnvironment): Boolean {
    return environment.contentToReuse != null && environment.getRunToolbarProcess() != null
  }
}

private class MyActionConfigurationCustomizer : ActionConfigurationCustomizer, ActionConfigurationCustomizer.LightCustomizeStrategy {
  override suspend fun customize(actionRegistrar: ActionRuntimeRegistrar) {
    if (ExperimentalUI.isNewUI()) {
      actionRegistrar.unregisterAction("RunToolbarWidgetAction")
    }
  }
}