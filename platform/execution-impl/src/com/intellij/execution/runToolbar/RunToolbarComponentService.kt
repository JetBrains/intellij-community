// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.runToolbar

import com.intellij.execution.ExecutionListener
import com.intellij.execution.ExecutionManager
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.ide.ui.ToolbarSettings
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project

internal class RunToolbarComponentService(private val project: Project): Disposable {
  companion object {
    private val LOG = logger<RunToolbarComponentService>()
  }

  private val extraSlots: RunToolbarSlotManager
    get() = RunToolbarSlotManager.getInstance(project)

  init {
    if (ToolbarSettings.getInstance().isAvailable) {
      project.messageBus.connect(this).subscribe(ExecutionManager.EXECUTION_TOPIC, object : ExecutionListener {
        override fun processNotStarted(executorId: String, env: ExecutionEnvironment) {
          ApplicationManager.getApplication().invokeLater {
            if (env.project == project && !project.isDisposed) {
              processNotStarted(env)
            }
          }
        }

        override fun processStarted(executorId: String, env: ExecutionEnvironment, handler: ProcessHandler) {
          ApplicationManager.getApplication().invokeLater {
            if (env.project == project && !project.isDisposed) {
              start(env)
            }
          }
        }

        override fun processTerminating(executorId: String, env: ExecutionEnvironment, handler: ProcessHandler) {
          ApplicationManager.getApplication().invokeLater {
            if (env.project == project && !project.isDisposed) {
              terminating(env)
            }
          }
        }

        override fun processTerminated(executorId: String, env: ExecutionEnvironment, handler: ProcessHandler, exitCode: Int) {
          ApplicationManager.getApplication().invokeLater {
            if (env.project == project && !project.isDisposed) {
              terminated(env)
            }
          }
        }
      })
    }
  }

  private fun start(env: ExecutionEnvironment) {
    if (isRelevant(env)) {
      if (RunToolbarProcess.logNeeded) LOG.info(
        "new active: ${env.executor.id} ${env}, slot manager ${if (extraSlots.active) "ENABLED" else "DISABLED"} RunToolbar")
      if (extraSlots.active) {
        extraSlots.processStarted(env)
      }
    }
  }

  private fun processNotStarted(env: ExecutionEnvironment) {
    if (env.getRunToolbarProcess() != null) {
      if (RunToolbarProcess.logNeeded) LOG.info("Not started: ${env.executor.id} ${env} RunToolbar")
      if (extraSlots.active) {
        extraSlots.processNotStarted(env)
      }
    }
  }

  private fun terminated(env: ExecutionEnvironment) {
      if (RunToolbarProcess.logNeeded) LOG.info(
        "removed: ${env.executor.id} ${env}, slot manager ${if (extraSlots.active) "ENABLED" else "DISABLED"} RunToolbar")
      if (extraSlots.active) {
        extraSlots.processTerminated(env.executionId)
      }
  }

  private fun terminating(env: ExecutionEnvironment) {
    if (RunToolbarProcess.logNeeded) LOG.info(
      "terminating: ${env.executor.id} ${env}, slot manager ${if (extraSlots.active) "ENABLED" else "DISABLED"} RunToolbar")
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

  override fun dispose() {
  }
}