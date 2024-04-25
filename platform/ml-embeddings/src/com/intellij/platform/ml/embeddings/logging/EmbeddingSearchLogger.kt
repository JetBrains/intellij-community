// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.embeddings.logging

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.internal.statistic.eventLog.events.VarargEventId
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.ml.embeddings.search.services.*
import kotlin.math.round

internal object EmbeddingSearchLogger : CounterUsagesCollector() {
  private val GROUP: EventLogGroup = EventLogGroup("ml.embeddings", 2)

  private val MODEL_VERSION = EventFields.StringValidatedByInlineRegexp("model_version", "\\d+.\\d+.\\d+")
  private val ENABLED_INDICES = EventFields.StringList("enabled_indices", listOf("actions", "files", "classes", "symbols"))
  private val USED_MEMORY_MB = EventFields.Double("used_memory_mb")
  private val VECTOR_COUNT = EventFields.Long("vector_count")
  private val INDEX_TYPE = EventFields.String("index_type", listOf("actions", "file-based"))
  private val INDEX = EventFields.Enum("index", Index::class.java) { it.name.lowercase() }

  private val COMMON_FIELDS = arrayOf(INDEX_TYPE, MODEL_VERSION, USED_MEMORY_MB, VECTOR_COUNT, ENABLED_INDICES, EventFields.DurationMs)

  private val INDEXING_FINISHED: VarargEventId = GROUP.registerVarargEvent("indexing.finished", *COMMON_FIELDS)
  private val INDEXING_LOADED: VarargEventId = GROUP.registerVarargEvent("indexing.loaded", *COMMON_FIELDS)
  private val INDEXING_SAVED: VarargEventId = GROUP.registerVarargEvent("indexing.saved", *COMMON_FIELDS)

  private val SEARCH_FINISHED: VarargEventId = GROUP.registerVarargEvent("search.finished", MODEL_VERSION,
                                                                         USED_MEMORY_MB, VECTOR_COUNT, INDEX, EventFields.DurationMs)

  private const val BYTES_IN_MEGABYTE = 1024L * 1024L

  override fun getGroup(): EventLogGroup = GROUP

  suspend fun indexingFinished(project: Project?, forActions: Boolean, durationMs: Long) {
    INDEXING_FINISHED.log(project, getCommonFields(project, forActions) + listOf(EventFields.DurationMs.with(durationMs)))
  }

  suspend fun indexingLoaded(project: Project?, forActions: Boolean, durationMs: Long) {
    INDEXING_LOADED.log(project, getCommonFields(project, forActions) + listOf(EventFields.DurationMs.with(durationMs)))
  }

  suspend fun indexingSaved(project: Project?, forActions: Boolean, durationMs: Long) {
    INDEXING_SAVED.log(project, getCommonFields(project, forActions) + listOf(EventFields.DurationMs.with(durationMs)))
  }

  suspend fun searchFinished(project: Project?, index: Index, durationMs: Long) {
    require(index == Index.ACTIONS || project != null)
    SEARCH_FINISHED.log(project, buildList {
      add(MODEL_VERSION.with(Registry.stringValue("intellij.platform.ml.embeddings.model.version")))
      add(USED_MEMORY_MB.with(
        roundDouble(
          when (index) {
            Index.ACTIONS -> ActionEmbeddingsStorage.getInstance().index.estimateMemoryUsage()
            Index.FILES -> FileEmbeddingsStorage.getInstance(project!!).index.estimateMemoryUsage()
            Index.CLASSES -> ClassEmbeddingsStorage.getInstance(project!!).index.estimateMemoryUsage()
            Index.SYMBOLS -> SymbolEmbeddingStorage.getInstance(project!!).index.estimateMemoryUsage()
          }.toDouble() / BYTES_IN_MEGABYTE
        )
      ))
      add(VECTOR_COUNT.with(
        when (index) {
          Index.ACTIONS -> ActionEmbeddingsStorage.getInstance().index.getSize()
          Index.FILES -> FileEmbeddingsStorage.getInstance(project!!).index.getSize()
          Index.CLASSES -> ClassEmbeddingsStorage.getInstance(project!!).index.getSize()
          Index.SYMBOLS -> SymbolEmbeddingStorage.getInstance(project!!).index.getSize()
        }.toLong()
      ))
      add(INDEX.with(index))
      add(EventFields.DurationMs.with(durationMs))
    })
  }

  private suspend fun getCommonFields(project: Project?, forActions: Boolean): List<EventPair<*>> {
    require(forActions || project != null)
    return buildList {
      add(MODEL_VERSION.with(Registry.stringValue("intellij.platform.ml.embeddings.model.version")))
      add(INDEX_TYPE.with(if (forActions) "actions" else "file-based"))
      add(ENABLED_INDICES.with(getEnabledIndices()))
      add(VECTOR_COUNT.with(getVectorCount(project, forActions)))
      add(USED_MEMORY_MB.with(getUsedMemory(project, forActions)))
    }
  }

  private fun getEnabledIndices(): List<String> {
    return buildList {
      EmbeddingIndexSettingsImpl.getInstance().run {
        if (shouldIndexActions) add("actions")
        if (shouldIndexFiles) add("files")
        if (shouldIndexClasses) add("classes")
        if (shouldIndexSymbols) add("symbols")
      }
    }
  }

  private suspend fun getVectorCount(project: Project?, forActions: Boolean): Long {
    var totalCount = 0L
    val settings = EmbeddingIndexSettingsImpl.getInstance()
    if (forActions && settings.shouldIndexActions) totalCount += ActionEmbeddingsStorage.getInstance().index.getSize()
    if (!forActions && project != null) {
      if (settings.shouldIndexFiles) totalCount += FileEmbeddingsStorage.getInstance(project).index.getSize()
      if (settings.shouldIndexClasses) totalCount += ClassEmbeddingsStorage.getInstance(project).index.getSize()
      if (settings.shouldIndexSymbols) totalCount += SymbolEmbeddingStorage.getInstance(project).index.getSize()
    }
    return totalCount
  }

  private suspend fun getUsedMemory(project: Project?, forActions: Boolean): Double {
    var totalMemory = 0L
    val settings = EmbeddingIndexSettingsImpl.getInstance()
    if (forActions && settings.shouldIndexActions) totalMemory += ActionEmbeddingsStorage.getInstance().index.estimateMemoryUsage()
    if (!forActions && project != null) {
      if (settings.shouldIndexFiles) totalMemory += FileEmbeddingsStorage.getInstance(project).index.estimateMemoryUsage()
      if (settings.shouldIndexClasses) totalMemory += ClassEmbeddingsStorage.getInstance(project).index.estimateMemoryUsage()
      if (settings.shouldIndexSymbols) totalMemory += SymbolEmbeddingStorage.getInstance(project).index.estimateMemoryUsage()
    }
    return roundDouble(totalMemory.toDouble() / BYTES_IN_MEGABYTE)
  }

  enum class Index { ACTIONS, FILES, CLASSES, SYMBOLS }

  private fun roundDouble(value: Double): Double {
    if (!value.isFinite()) return -1.0
    return round(value * 10000) / 10000
  }
}