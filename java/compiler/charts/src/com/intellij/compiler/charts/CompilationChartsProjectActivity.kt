// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compiler.charts

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.intellij.build.BuildProgressListener
import com.intellij.build.BuildViewManager
import com.intellij.build.events.FinishBuildEvent
import com.intellij.build.events.StartBuildEvent
import com.intellij.compiler.charts.jps.ChartsBuilderService.COMPILATION_STATISTIC_BUILDER_ID
import com.intellij.compiler.charts.jps.CompileStatisticBuilderMessage.*
import com.intellij.compiler.charts.ui.CompilationChartsBuildEvent
import com.intellij.compiler.server.CustomBuilderMessageHandler
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.Disposer
import com.intellij.util.messages.MessageBusConnection
import java.util.*

class CompilationChartsProjectActivity : ProjectActivity {
  companion object {
    private val LOG: Logger = Logger.getInstance(CompilationChartsProjectActivity::class.java)
  }

  override suspend fun execute(project: Project) {

    val view = project.getService(BuildViewManager::class.java)
    val disposable = Disposer.newDisposable(view, "Compilation charts event listener disposable")

    val connection: MessageBusConnection = project.messageBus.connect()
    val handler = CompilationChartsMessageHandler()
    connection.subscribe(CustomBuilderMessageHandler.TOPIC, handler)

    view.addListener(BuildProgressListener { buildId, event ->
      when (event) {
        is StartBuildEvent -> {
          val title = event.buildDescriptor.title.lowercase()
          if (title.contains("up-to-date") || title.startsWith("worksheet")) return@BuildProgressListener

          val chartEvent = CompilationChartsBuildEvent(buildId)
          view.onEvent(buildId, chartEvent)
          handler.addState(chartEvent.vm());
        }
        is FinishBuildEvent -> handler.removeState()
      }
    }, disposable)
  }

  private class CompilationChartsMessageHandler : CustomBuilderMessageHandler {
    private val json = ObjectMapper(JsonFactory())
    private val states: Queue<CompilationChartsViewModel> = ArrayDeque()
    private var currentState: CompilationChartsViewModel? = null

    fun addState(vm: CompilationChartsViewModel) {
      states.add(vm)
      if (currentState == null) removeState()
    }

    fun removeState() {
      currentState = states.poll()
    }

    override fun messageReceived(builderId: String?, messageType: String?, messageText: String?) {
      if (builderId != COMPILATION_STATISTIC_BUILDER_ID) return
      try {
        when (messageType) {
          "STARTED" -> {
            val values = json.readValue(messageText, object : TypeReference<List<StartTarget>>() {})
            currentState?.started(values)
          }
          "FINISHED" -> {
            val values = json.readValue(messageText, object : TypeReference<List<FinishTarget>>() {})
            currentState?.finished(values)
          }
          "STATISTIC" -> {
            val value = json.readValue(messageText, CpuMemoryStatistics::class.java)
            currentState?.statistic(value)
          }
        }
      }
      catch (e: JsonProcessingException) {
        LOG.warn("Failed to parse message: $messageText", e)
      }
    }
  }
}