// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.diagnostic

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonTypeName
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.util.indexing.diagnostic.dto.JsonDateTime
import com.intellij.util.indexing.diagnostic.dto.JsonDuration
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.io.path.exists
import kotlin.io.path.readLines
import kotlin.io.path.writeLines

object ChangedFilesPushedDiagnostic {
  private val LOG = thisLogger()

  fun addEvent(project: Project, event: ChangedFilesPushingStatistics) {
    appendEvent(project, ChangedFilesPushedEvent(event.reason, event.startTime, JsonDuration(event.duration), event.isCancelled))
  }

  fun readEvents(project: Project): List<ChangedFilesPushedEvent> {
    val eventsFile = getEventsFile(project)
    if (!eventsFile.exists()) {
      return emptyList()
    }
    try {
      return eventsFile.readLines().map { jacksonMapper.readValue(it) }
    }
    catch (e: Exception) {
      LOG.warn("Failed to read $eventsFile", e)
      return emptyList()
    }
  }

  private fun appendEvent(project: Project, event: ChangedFilesPushedEvent) {
    try {
      val file = getEventsFile(project)
      val jsonLine = jacksonMapper.writeValueAsString(event).replace("\n", " ")
      file.writeLines(listOf(jsonLine), Charsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND)
    }
    catch (e: Exception) {
      LOG.warn("Failed to append changed files pushing event: $event", e)
    }
  }

  private fun getEventsFile(project: Project): Path =
    IndexDiagnosticDumper.getProjectDiagnosticDirectory(project).resolve("changed-files-pushing-events.json")

  private val jacksonMapper: ObjectMapper by lazy {
    jacksonObjectMapper().registerKotlinModule()
  }
}

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeName("event")
data class ChangedFilesPushedEvent(val reason: String, val startTime: JsonDateTime, val duration: JsonDuration, val isCancelled: Boolean)

