// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.segmentedRunDebugWidget

import com.intellij.application.subscribe
import com.intellij.execution.ExecutionListener
import com.intellij.execution.ExecutionManager
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.stateExecutionWidget.StateWidgetProcess
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.util.messages.Topic
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Function

internal class StateWidgetManager(val project: Project) {
  @ApiStatus.Internal
  interface StateWidgetManagerListener {
    fun configurationChanged()
  }

  companion object {
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

  private val executorSessions: MutableMap<String, MutableMap<Long, ExecutionEnvironment>> = ConcurrentHashMap()
  private val executorProcessMap: MutableMap<String, StateWidgetProcess> = ConcurrentHashMap()
  private val processMap: MutableMap<String, StateWidgetProcess> = ConcurrentHashMap()

  init {
    ApplicationManager.getApplication().invokeLater {
      val processes = StateWidgetProcess.getProcesses()
      processes.forEach {
        executorProcessMap[it.executorId] = it
        processMap[it.ID] = it
      }

      ExecutionManager.EXECUTION_TOPIC.subscribe(project, object : ExecutionListener {
        override fun processStarted(executorId: String, env: ExecutionEnvironment, handler: ProcessHandler) {
          processes.forEach {
            if (it.executorId == executorId) {
              executorSessions.computeIfAbsent(executorId, Function { ConcurrentHashMap() })[env.executionId] = env
              fireConfigurationChanged()
            }
          }
        }

        override fun processTerminated(executorId: String, env: ExecutionEnvironment, handler: ProcessHandler, exitCode: Int) {
          executorSessions[executorId]?.let {
            it.remove(env.executionId)
            if (it.isEmpty()) executorSessions.remove(executorId)
          }
          fireConfigurationChanged()
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

  fun getActiveProcessesIDs(): Set<String> {
    return getActiveProcesses().map { it.ID }.toSet()
  }

  fun getActiveProcesses(): Set<StateWidgetProcess> {
    return executorSessions.keys.mapNotNull { executorProcessMap[it] }.toSet()
  }

  fun getProcessById(processId: String): StateWidgetProcess? {
    return executorProcessMap[processId]
  }
}