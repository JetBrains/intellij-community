// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.segmentedRunDebugWidget

import com.intellij.application.subscribe
import com.intellij.execution.ExecutionListener
import com.intellij.execution.ExecutionManager
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.executors.ExecutorGroup
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.stateExecutionWidget.StateWidgetProcess
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.util.messages.Topic
import org.jetbrains.annotations.ApiStatus
import java.util.function.Function

class StateWidgetManager(val project: Project) {
  @ApiStatus.Internal
  interface StateWidgetManagerListener {
    fun configurationChanged()
  }

  companion object {
    private val LOGGER = Logger.getInstance(StateWidgetManager::class.java)

    @JvmStatic
    fun getInstance(project: Project): StateWidgetManager = project.service()

    @Topic.AppLevel
    @ApiStatus.Internal
    @JvmField
    val TOPIC = Topic(StateWidgetManagerListener::class.java, Topic.BroadcastDirection.NONE)

    @JvmStatic
    private fun fireConfigurationChanged() {
      ActionToolbarImpl.updateAllToolbarsImmediately(true)
      ApplicationManager.getApplication().messageBus.syncPublisher(TOPIC).configurationChanged()
    }
  }

  private val processById: MutableMap<String, StateWidgetProcess> = mutableMapOf()
  private val processByExecutorId: MutableMap<String, StateWidgetProcess> = mutableMapOf()

  private val executions: MutableMap<Long, ExecutionEnvironment> = mutableMapOf()
  private val processIdByExecutionId: MutableMap<Long, String> = mutableMapOf()
  private val executionsByExecutorId: MutableMap<String, MutableSet<Long>> = mutableMapOf()

  private val activeProcessByConfiguration: MutableMap<RunnerAndConfigurationSettings?, MutableSet<StateWidgetProcess>> = mutableMapOf()
  private val executionByConfiguration: MutableMap<RunnerAndConfigurationSettings?, MutableSet<Long>> = mutableMapOf()

  private val processes = StateWidgetProcess.getProcesses()


  private val terminatedBeforeStart = mutableListOf<Long>()

  init {
    processes.forEach {
      processById[it.ID] = it
    }

    if (StateWidgetProcess.isAvailable()) {
      ExecutionManager.EXECUTION_TOPIC.subscribe(project, object : ExecutionListener {
        override fun processStarted(executorId: String, env: ExecutionEnvironment, handler: ProcessHandler) {
          ApplicationManager.getApplication().invokeLater {
            if(LOGGER.isTraceEnabled) {
              LOGGER.trace("=============Execution started: ${env.executionId} executor: $executorId" +
                          "${if(handler.isProcessTerminated) "terminated" else if(handler.isProcessTerminating) " terminating" else ""} ")
            }
            if(terminatedBeforeStart.contains(env.executionId)) {
              LOGGER.warn("processStarted notification for a process that has already been terminated. ${env.executionId} executor: $executorId")
              return@invokeLater
            }
            start(executorId, env)
          }
        }

        override fun processTerminated(executorId: String, env: ExecutionEnvironment, handler: ProcessHandler, exitCode: Int) {
          ApplicationManager.getApplication().invokeLater {
            if(LOGGER.isTraceEnabled) {
              LOGGER.trace("=============Execution stopped: " +
                           "${env.executionId}, " +
                           "executor: $executorId, " +
                           "exitCode: $exitCode, " +
                           "${if(handler.isProcessTerminated) "terminated" else if(handler.isProcessTerminating) " terminating" else ""} ")
            }

            if(executions[env.executionId] == null) {
              terminatedBeforeStart.add(env.executionId)

              LOGGER.warn("processTerminated notification before processStarted notification. ${env.executionId} executor: $executorId")
              return@invokeLater
            }
            stop(executorId, env)
          }
        }
      })
    }
  }

  private fun start(executorId: String, env: ExecutionEnvironment) {
    ExecutorGroup.getGroupIfProxy(env.executor)?.let { executorGroup ->
      processes.forEach { process ->
        if (process.executorId == executorGroup.id) {
          collect(process, executorId, env)
          return
        }
      }
      return
    }

    processes.forEach { process ->
      if (process.executorId == executorId) {
        collect(process, executorId, env)
        return
      }
    }
  }

  private fun stop(executorId: String, env: ExecutionEnvironment) {
    executions.remove(env.executionId)
    processIdByExecutionId.remove(env.executionId)

    remove(executionsByExecutorId, executorId, env.executionId)
    remove(executionByConfiguration, env.runnerAndConfigurationSettings, env.executionId)

    update()
  }

  private fun <K> remove(map: MutableMap<K, MutableSet<Long>>, key: K, id: Long){
    map[key]?.let{
      it.remove(id)
      if(it.isEmpty()) map.remove(key)
    }
  }

  private fun collect(process: StateWidgetProcess, executorId: String, env: ExecutionEnvironment) {
    executions[env.executionId] = env
    executionsByExecutorId.computeIfAbsent(executorId, Function { mutableSetOf() }).add(env.executionId)
    processIdByExecutionId[env.executionId] = process.ID
    processByExecutorId[executorId] = process
    executionByConfiguration.computeIfAbsent(env.runnerAndConfigurationSettings, Function { mutableSetOf() }).add(env.executionId)
    update()
  }

  private fun update() {
    updateRunningConfiguration()
    fireConfigurationChanged()
    logging()
  }

  private fun logging() {
    if(LOGGER.isTraceEnabled) {
      LOGGER.trace("executions:${if(executions.isEmpty()) "empty" else ""}")
      executions.forEach {
        LOGGER.trace("       ${it.value.executor.actionName}: settings ${it.value.runnerAndConfigurationSettings?.name}, id: ${it.key}")
      }
      LOGGER.trace("processIdByExecutionId:${if(processIdByExecutionId.isEmpty()) "empty" else ""}")
      processIdByExecutionId.forEach {
        LOGGER.trace("       ${it.key}: ${it.value}")
      }

      LOGGER.trace("executionsByExecutorId:${if(executionsByExecutorId.isEmpty()) "empty" else ""}")
      executionsByExecutorId.forEach { entry ->
        LOGGER.trace("       ${entry.key}: ${entry.value.joinToString ("|")}")
      }

      LOGGER.trace("activeProcessByConfiguration:${if(activeProcessByConfiguration.isEmpty()) "empty" else ""}")
      activeProcessByConfiguration.forEach { entry ->
        LOGGER.trace("       ${entry.key?.name}: ${entry.value.joinToString("|") { it.ID }}")
      }

      LOGGER.trace("executionByConfiguration:${if(executionByConfiguration.isEmpty()) "empty" else ""}")
      executionByConfiguration.forEach { entry ->
        LOGGER.trace("       ${entry.key?.name}: ${entry.value.joinToString ("|")}")
      }
    }
  }

  private fun updateRunningConfiguration() {
    activeProcessByConfiguration.clear()

    executions.values.forEach { env ->
      processByExecutorId[env.executor.id]?.let { process ->
        activeProcessByConfiguration.computeIfAbsent(env.runnerAndConfigurationSettings, Function { mutableSetOf() }).add(process)
      }
    }
  }

  fun getExecutionsCount(): Int {
    return executions.count()
  }

  fun getActiveProcessesIDs(): Set<String> {
    return getActiveProcesses().map { it.ID }.toSet()
  }

  fun getActiveProcesses(): Set<StateWidgetProcess> {
    return executionsByExecutorId.keys.mapNotNull { processByExecutorId[it] }.toSet()
  }

  fun getActiveProcessesBySettings(configuration: RunnerAndConfigurationSettings): Set<StateWidgetProcess>? {
    return activeProcessByConfiguration[configuration]
  }

  fun getExecutionBySettings(configuration: RunnerAndConfigurationSettings): Set<ExecutionEnvironment>? {
    return executionByConfiguration[configuration]?.mapNotNull { executions[it] }?.toSet()
  }

  fun getExecutionByExecutionId(executionId: Long): ExecutionEnvironment? {
    return executions[executionId]
  }

  fun getProcessByExecutionId(executionId: Long): StateWidgetProcess? {
    return processIdByExecutionId[executionId]?.let { processById[it] }
  }

  fun getActiveExecutionEnvironments(): Set<ExecutionEnvironment> {
    return executions.values.toSet()
  }
}