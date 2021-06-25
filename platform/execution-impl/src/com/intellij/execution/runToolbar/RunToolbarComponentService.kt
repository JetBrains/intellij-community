// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.runToolbar

import com.intellij.application.subscribe
import com.intellij.execution.ExecutionListener
import com.intellij.execution.ExecutionManager
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project

class RunToolbarComponentService(project: Project) {
  companion object {
    private val LOGGER = Logger.getInstance(RunToolbarComponentService::class.java)
  }

  private val extraSlots =  RunToolbarSlotManager.getInstance(project)

  private val executions: MutableMap<Long, ExecutionEnvironment> = mutableMapOf()
  init {
    if (RunToolbarProcess.isAvailable()) {
      ExecutionManager.EXECUTION_TOPIC.subscribe(project, object : ExecutionListener {
        override fun processStarted(executorId: String, env: ExecutionEnvironment, handler: ProcessHandler) {
          ApplicationManager.getApplication().invokeLater {
            if(LOGGER.isTraceEnabled) {
              LOGGER.trace("Execution started: ${env.executionId} executor: $executorId" +
                                              "${if(handler.isProcessTerminated) "terminated" else if(handler.isProcessTerminating) " terminating" else ""} ")
            }
            start(env)
          }
        }

        override fun processTerminated(executorId: String, env: ExecutionEnvironment, handler: ProcessHandler, exitCode: Int) {
          ApplicationManager.getApplication().invokeLater {
            if(LOGGER.isTraceEnabled) {
              LOGGER.trace("Execution stopped: " +
                                              "${env.executionId}, " +
                                              "executor: $executorId, " +
                                              "exitCode: $exitCode, " +
                                              "${if(handler.isProcessTerminated) "terminated" else if(handler.isProcessTerminating) " terminating" else ""} ")
            }
            stop(env)
          }
        }
      })
    }
  }

  private fun start(env: ExecutionEnvironment) {
    if(isRelevant(env)) {
      executions[env.executionId] = env
      extraSlots.processStarted(env)
    }
  }

  private fun stop(env: ExecutionEnvironment) {
    if(isRelevant(env)) {
      executions.remove(env.executionId)
      extraSlots.processStopped(env.executionId)
    }
  }

  private fun isRelevant(environment: ExecutionEnvironment): Boolean {
    return environment.getRunToolbarProcess() != null
  }
}