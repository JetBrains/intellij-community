// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.compiler.charts

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.intellij.build.BuildProgressListener
import com.intellij.build.BuildViewManager
import com.intellij.build.events.FinishBuildEvent
import com.intellij.build.events.StartBuildEvent
import com.intellij.compiler.server.CustomBuilderMessageHandler
import com.intellij.java.compiler.charts.ui.CompilationChartsBuildEvent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.messages.MessageBusConnection
import java.util.*

class CompilationChartsProjectActivity : ProjectActivity {
  companion object {
    private val LOG: Logger = Logger.getInstance(CompilationChartsProjectActivity::class.java)
    const val COMPILATION_CHARTS_KEY: String = "compilation.charts"
    const val COMPILATION_STATISTIC_BUILDER_ID: String = "jps.compile.statistic"
    const val COMPILATION_STATUS_BUILDER_ID: String = "jps.compile.status"
  }

  override suspend fun execute(project: Project) {
    if (!Registry.`is`(COMPILATION_CHARTS_KEY)) return

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

          CompilationChartsBuildEvent(project, view, buildId).also { chartEvent ->
            handler.addState(chartEvent)
          }
        }
        is FinishBuildEvent -> handler.removeState()
      }
    }, disposable)
  }

  private class CompilationChartsMessageHandler : CustomBuilderMessageHandler {
    private val json = ObjectMapper(JsonFactory()).registerModule(KotlinModule.Builder().build())
    private val states: Queue<CompilationChartsBuildEvent> = ArrayDeque()
    private var currentState: CompilationChartsBuildEvent? = null
    private val defaultUUID: UUID = UUID.randomUUID()

    fun addState(event: CompilationChartsBuildEvent) {
      states.add(event)
      if (currentState == null) removeState()
    }

    fun removeState() {
      currentState = states.poll()
    }

    override fun messageReceived(builderId: String?, messageType: String?, messageText: String?) {
      messageReceived(defaultUUID, builderId, messageType, messageText)
    }

    override fun messageReceived(sessionId: UUID, builderId: String?, messageType: String?, messageText: String?) {
      when (builderId) {
        COMPILATION_STATUS_BUILDER_ID -> status(messageType)
        COMPILATION_STATISTIC_BUILDER_ID -> statistic(messageType, messageText)
      }
    }

    private fun status(messageType: String?) {
      when (messageType) {
        "START" -> currentState?.run { view.onEvent(buildId, this) }
        "FINISH" -> {}
      }
    }

    fun statistic(messageType: String?, messageText: String?) {
      try {
        when (messageType) {
          "STARTED" -> {
            val values = json.readValue(messageText, object : TypeReference<List<StartTarget>>() {})
            currentState?.run { vm().started(values) }
          }
          "FINISHED" -> {
            val values = json.readValue(messageText, object : TypeReference<List<FinishTarget>>() {})
            currentState?.run { vm().finished(values) }
          }
          "STATISTIC" -> {
            val value = json.readValue(messageText, CpuMemoryStatistics::class.java)
            currentState?.run { vm().statistic(value) }
          }
        }
      }
      catch (e: JsonProcessingException) {
        LOG.warn("Failed to parse message: $messageText", e)
      }
    }
  }
}