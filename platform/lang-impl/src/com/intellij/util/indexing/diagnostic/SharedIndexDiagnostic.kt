// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.diagnostic

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.util.indexing.diagnostic.dto.*
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.ZonedDateTime
import kotlin.io.path.exists
import kotlin.io.path.readLines
import kotlin.io.path.writeLines

object SharedIndexDiagnostic {

  private val LOG = Logger.getInstance(SharedIndexDiagnostic::class.java)

  fun onIndexAttachSuccess(
    project: Project,
    kind: String,
    indexName: String,
    chunkUniqueId: String,
    fbMatch: JsonPercentages,
    stubMatch: JsonPercentages
  ) {
    appendEvent(
      project,
      JsonSharedIndexDiagnosticEvent.Attached.Success(
        time = JsonSharedIndexDiagnosticEvent.now(),
        kind = kind,
        chunkUniqueId = chunkUniqueId,
        indexName = indexName,
        fbMatch = fbMatch,
        stubMatch = stubMatch
      )
    )
  }

  fun onIndexAttachIncompatible(project: Project, kind: String, indexId: String) {
    appendEvent(project, JsonSharedIndexDiagnosticEvent.Attached.Incompatible(
      time = JsonSharedIndexDiagnosticEvent.now(), kind = kind, chunkUniqueId = indexId
    ))
  }

  fun onIndexAttachNotFound(project: Project, kind: String, indexId: String) {
    appendEvent(project, JsonSharedIndexDiagnosticEvent.Attached.NotFound(
      time = JsonSharedIndexDiagnosticEvent.now(), kind = kind, chunkUniqueId = indexId
    ))
  }

  fun onIndexAttachExcluded(project: Project, kind: String, indexId: String) {
    appendEvent(project, JsonSharedIndexDiagnosticEvent.Attached.Excluded(
      time = JsonSharedIndexDiagnosticEvent.now(), kind = kind, chunkUniqueId = indexId
    ))
  }

  fun readEvents(project: Project): List<JsonSharedIndexDiagnosticEvent> {
    val eventsFile = getEventsFile(project)
    if (!eventsFile.exists()) {
      return emptyList()
    }
    return try {
      eventsFile.readLines().map { jacksonMapper.readValue(it) }
    } catch (e: Exception) {
      LOG.warn("Failed to read $eventsFile", e)
      return emptyList()
    }
  }

  fun onIndexDownloaded(
    project: Project?,
    kind: String,
    indexId: String,
    finishType: String,
    downloadTimeNano: Long,
    packedSizeBytes: Long,
    unpackedSizeBytes: Long,
    generationTime: ZonedDateTime?
  ) {
    val downloadTime = JsonDuration(downloadTimeNano)
    val event = JsonSharedIndexDiagnosticEvent.Downloaded(
      time = JsonSharedIndexDiagnosticEvent.now(),
      kind = kind,
      chunkUniqueId = indexId,
      finishType = finishType,
      downloadTime = downloadTime,
      packedSize = JsonFileSize(packedSizeBytes),
      unpackedSize = JsonFileSize(unpackedSizeBytes),
      downloadSpeed = JsonProcessingSpeed(packedSizeBytes, downloadTimeNano),
      generationTime = generationTime?.let { JsonDateTime(it) }
    )
    if (project != null) {
      appendEvent(project, event)
    }
  }

  private val jacksonMapper: ObjectMapper by lazy {
    jacksonObjectMapper().registerKotlinModule()
  }

  private fun getEventsFile(project: Project): Path =
    IndexDiagnosticDumper.getProjectDiagnosticDirectory(project).resolve("shared-index-events.json")

  private fun appendEvent(project: Project, event: JsonSharedIndexDiagnosticEvent) {
    try {
      val file = getEventsFile(project)
      val jsonLine = jacksonMapper.writeValueAsString(event).replace("\n", " ")
      file.writeLines(listOf(jsonLine), Charsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND)
    }
    catch (e: Exception) {
      LOG.warn("Failed to append shared index event: $event", e)
    }
  }
}

// String presentation of ID<*>
typealias JsonIndexId = String

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
sealed class JsonSharedIndexDiagnosticEvent {

  companion object {
    fun now(): JsonDateTime = JsonDateTime(ZonedDateTime.now())
  }

  abstract val time: JsonDateTime
  abstract val kind: String
  abstract val chunkUniqueId: String

  @JsonIgnoreProperties(ignoreUnknown = true)
  @JsonTypeName("downloaded")
  data class Downloaded(
    override val time: JsonDateTime = JsonDateTime(),
    override val kind: String = "",
    override val chunkUniqueId: String = "",
    val finishType: String = "",
    val downloadTime: JsonDuration = JsonDuration(),
    val packedSize: JsonFileSize = JsonFileSize(),
    val unpackedSize: JsonFileSize = JsonFileSize(),
    val downloadSpeed: JsonProcessingSpeed = JsonProcessingSpeed(),
    val generationTime: JsonDateTime?
  ) : JsonSharedIndexDiagnosticEvent()

  sealed class Attached : JsonSharedIndexDiagnosticEvent() {
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonTypeName("attached")
    data class Success(
      override val time: JsonDateTime = JsonDateTime(),
      override val kind: String = "",
      override val chunkUniqueId: String = "",
      val indexName: String,
      val fbMatch: JsonPercentages,
      val stubMatch: JsonPercentages
    ) : Attached()

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonTypeName("attachIncompatible")
    data class Incompatible(
      override val time: JsonDateTime = JsonDateTime(),
      override val kind: String = "",
      override val chunkUniqueId: String = ""
    ) : Attached()

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonTypeName("notFound")
    data class NotFound(
      override val time: JsonDateTime = JsonDateTime(),
      override val kind: String = "",
      override val chunkUniqueId: String = ""
    ) : Attached()

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonTypeName("excluded")
    data class Excluded(
      override val time: JsonDateTime = JsonDateTime(),
      override val kind: String = "",
      override val chunkUniqueId: String = ""
    ) : Attached()
  }
}