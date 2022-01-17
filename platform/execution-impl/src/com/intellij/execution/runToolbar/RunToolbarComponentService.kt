// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.runToolbar

import com.intellij.application.subscribe
import com.intellij.execution.ExecutionListener
import com.intellij.execution.ExecutionManager
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project

class RunToolbarComponentService(val project: Project) {
  companion object {
    private val LOG = Logger.getInstance(RunToolbarComponentService::class.java)
  }
  private val extraSlots = RunToolbarSlotManager.getInstance(project)

  private val executions: MutableMap<Long, ExecutionEnvironment> = mutableMapOf()

  init {
    if (RunToolbarProcess.isAvailable) {
      ExecutionManager.EXECUTION_TOPIC.subscribe(project, object : ExecutionListener {
        override fun processStarted(executorId: String, env: ExecutionEnvironment, handler: ProcessHandler) {
          ApplicationManager.getApplication().invokeLater {
            if (env.project == project) {
              start(env)
            }
          }
        }

        override fun processTerminating(executorId: String, env: ExecutionEnvironment, handler: ProcessHandler) {
          ApplicationManager.getApplication().invokeLater {
            if (env.project == project) {
              terminating(env)
            }
          }
        }

        override fun processTerminated(executorId: String, env: ExecutionEnvironment, handler: ProcessHandler, exitCode: Int) {
          ApplicationManager.getApplication().invokeLater {
            if (env.project == project) {
              terminated(env)
            }
          }
        }
      })

      extraSlots.addListener(object : ActiveListener {
        override fun enabled() {
          if(RunToolbarProcess.logNeeded) LOG.info( "ENABLED. put data ${executions.map{it.value}.map{"$it (${it.executionId}); "}} RunToolbar" )
          executions.forEach{
            extraSlots.processStarted(it.value)
          }
        }

        override fun disabled() {
          if(RunToolbarProcess.logNeeded) LOG.info("DISABLED RunToolbar" )
          super.disabled()
        }
      })
    }
  }

  private fun start(env: ExecutionEnvironment) {
    if(isRelevant(env)) {
      executions[env.executionId] = env
      if(RunToolbarProcess.logNeeded) LOG.info("new active: ${env.executor.id} ${env}, slot manager ${if(extraSlots.active) "ENABLED" else "DISABLED"} RunToolbar" )
      if(extraSlots.active) {
        extraSlots.processStarted(env)
      }
    }
  }

  private fun terminated(env: ExecutionEnvironment) {
    if(isRelevant(env)) {
      executions.remove(env.executionId)
      if(RunToolbarProcess.logNeeded) LOG.info("removed: ${env.executor.id} ${env}, slot manager ${if(extraSlots.active) "ENABLED" else "DISABLED"} RunToolbar" )
      if(extraSlots.active) {
        extraSlots.processTerminated(env.executionId)
      }
    }
  }

  private fun terminating(env: ExecutionEnvironment) {
    if(isRelevant(env)) {
      if(RunToolbarProcess.logNeeded) LOG.info("terminating: ${env.executor.id} ${env}, slot manager ${if(extraSlots.active) "ENABLED" else "DISABLED"} RunToolbar" )
      if(extraSlots.active) {
        extraSlots.processTerminating(env)
      }
    }
  }

  private fun isRelevant(environment: ExecutionEnvironment): Boolean {
    return environment.contentToReuse != null && environment.getRunToolbarProcess() != null
  }
}