// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.segmentedRunDebugWidget

import com.intellij.application.subscribe
import com.intellij.execution.ExecutionListener
import com.intellij.execution.ExecutionManager
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.stateExecutionWidget.StateWidgetProcess
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.util.messages.Topic
import org.jetbrains.annotations.ApiStatus
import java.util.function.Function

internal class StateWidgetManager(val project: Project) {
  @ApiStatus.Internal
  interface StateWidgetManagerListener {
    fun configurationChanged()
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): StateWidgetManager = project.service()

    @Topic.AppLevel
    @ApiStatus.Internal
    @JvmField
    val TOPIC = Topic(StateWidgetManagerListener::class.java, Topic.BroadcastDirection.NONE)

    @JvmStatic
    fun fireConfigurationChanged() {
      ApplicationManager.getApplication().messageBus.syncPublisher(TOPIC).configurationChanged()
    }
  }

  private var sessionsCount: Int = 0
  private val executorSessions: MutableMap<String, MutableMap<Long, ExecutionEnvironment>> = mutableMapOf()
  private val executorProcessMap: MutableMap<String, StateWidgetProcess> = mutableMapOf()
  private val processMap: MutableMap<String, StateWidgetProcess> = mutableMapOf()
  private val runningConfigurations: MutableMap<RunnerAndConfigurationSettings?, MutableSet<StateWidgetProcess>> = mutableMapOf()

  init {
    ApplicationManager.getApplication().invokeLater {
      if(Disposer.isDisposed(project)) return@invokeLater

      val processes = StateWidgetProcess.getProcesses()
      processes.forEach {
        executorProcessMap[it.executorId] = it
        processMap[it.ID] = it
      }

      ExecutionManager.EXECUTION_TOPIC.subscribe(project, object : ExecutionListener {
        override fun processStarted(executorId: String, env: ExecutionEnvironment, handler: ProcessHandler) {
          processes.forEach {
            if (it.executorId == executorId) {
              executorSessions.computeIfAbsent(executorId, Function { mutableMapOf() })[env.executionId] = env
              update()
            }
          }
        }

        override fun processTerminated(executorId: String, env: ExecutionEnvironment, handler: ProcessHandler, exitCode: Int) {
          executorSessions[executorId]?.let {
            it.remove(env.executionId)
            if (it.isEmpty()) executorSessions.remove(executorId)
          }
          update()
        }

        private fun update() {
          sessionsCount = executorSessions.values.map { it.values.count() }.sum()

          updateRunningConfiguration()
          fireConfigurationChanged()
        }

        private fun updateRunningConfiguration() {
          runningConfigurations.clear()

          executorSessions.forEach { entry ->
            executorProcessMap[entry.key]?.let { process ->
              entry.value.values.map { it.runnerAndConfigurationSettings }.forEach {
                runningConfigurations.computeIfAbsent(it, Function { mutableSetOf() }).add(process)
              }
            }
          }
          runningConfigurations
        }
      })
    }

    /*    app.messageBus.connect().subscribe(TOPIC, object : StateWidgetManagerListener {
          override fun configurationChanged() {
            println("\n\n NEW")
            executorSessions.forEach { (key, map) ->
              print("$key ${map.size}; ")
            }
          }
        })*/
  }

  fun getActiveCount(): Int {
    return sessionsCount
  }

  fun getActiveProcessesIDs(): Set<String> {
    return getActiveProcesses().map { it.ID }.toSet()
  }

  fun getActiveProcesses(): Set<StateWidgetProcess> {
    return executorSessions.keys.mapNotNull { executorProcessMap[it] }.toSet()
  }

  fun getExecutorProcesses(): MutableMap<String, StateWidgetProcess> = executorProcessMap

  fun getProcessById(processId: String): StateWidgetProcess? {
    return executorProcessMap[processId]
  }

  fun getActiveProcessesBySettings(configuration: RunnerAndConfigurationSettings): Set<StateWidgetProcess?>? {
    return runningConfigurations[configuration]
  }
}