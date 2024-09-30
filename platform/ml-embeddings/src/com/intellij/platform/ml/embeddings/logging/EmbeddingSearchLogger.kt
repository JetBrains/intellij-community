// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.embeddings.logging

import com.intellij.ide.impl.ProjectUtil
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.internal.statistic.eventLog.events.VarargEventId
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.ml.embeddings.indexer.FileBasedEmbeddingIndexer.Companion.FILE_BASED_INDICES
import com.intellij.platform.ml.embeddings.indexer.IndexId
import com.intellij.platform.ml.embeddings.indexer.configuration.EmbeddingsConfiguration.Companion.getStorageManagerWrapper
import org.jetbrains.annotations.ApiStatus
import kotlin.math.round

object EmbeddingSearchLogger : CounterUsagesCollector() {
  private val GROUP: EventLogGroup = EventLogGroup("ml.embeddings", 3)

  private val MODEL_VERSION = EventFields.StringValidatedByInlineRegexp("model_version", "\\d+.\\d+.\\d+")
  private val ENABLED_INDICES = EventFields.StringList("enabled_indices", listOf("actions", "files", "classes", "symbols"))
  private val USED_MEMORY_MB = EventFields.Double("used_memory_mb")
  private val VECTOR_COUNT = EventFields.Long("vector_count")
  private val INDEX_TYPE = EventFields.String("index_type", listOf("actions", "file-based"))
  private val INDEX = EventFields.Enum("index", IndexId::class.java) { it.name.lowercase() }

  private val COMMON_FIELDS = arrayOf(INDEX_TYPE, MODEL_VERSION, USED_MEMORY_MB, VECTOR_COUNT, ENABLED_INDICES, EventFields.DurationMs)

  private val INDEXING_FINISHED: VarargEventId = GROUP.registerVarargEvent("indexing.finished", *COMMON_FIELDS)
  private val INDEXING_LOADED: VarargEventId = GROUP.registerVarargEvent("indexing.loaded", *COMMON_FIELDS)
  private val INDEXING_SAVED: VarargEventId = GROUP.registerVarargEvent("indexing.saved", *COMMON_FIELDS)

  private val SEARCH_FINISHED: VarargEventId = GROUP.registerVarargEvent("search.finished", MODEL_VERSION,
                                                                         USED_MEMORY_MB, VECTOR_COUNT, INDEX, EventFields.DurationMs)

  private const val BYTES_IN_MEGABYTE = 1024L * 1024L

  override fun getGroup(): EventLogGroup = GROUP

  suspend fun indexingFinished(project: Project?, forActions: Boolean, durationMs: Long) {
    INDEXING_FINISHED.log(
      project ?: ProjectUtil.getActiveProject(),
      getCommonFields(project, forActions) + listOf(EventFields.DurationMs.with(durationMs))
    )
  }

  suspend fun searchFinished(project: Project?, index: IndexId, durationMs: Long) {
    require(index == IndexId.ACTIONS || project != null)
    val storageStats = getStorageManagerWrapper(index).getStorageStats(project)
    SEARCH_FINISHED.log(project, buildList {
      add(MODEL_VERSION.with(Registry.stringValue("intellij.platform.ml.embeddings.model.version")))
      add(USED_MEMORY_MB.with(roundDouble(storageStats.bytes.toDouble() / BYTES_IN_MEGABYTE)))
      add(VECTOR_COUNT.with(storageStats.size.toLong()))
      add(INDEX.with(index))
      add(EventFields.DurationMs.with(durationMs))
    })
  }

  @Deprecated("Might not be relevant to all possible index implementations")
  @ApiStatus.ScheduledForRemoval
  suspend fun indexingLoaded(project: Project?, forActions: Boolean, durationMs: Long) {
    INDEXING_LOADED.log(project, getCommonFields(project, forActions) + listOf(EventFields.DurationMs.with(durationMs)))
  }

  @Deprecated("Might not be relevant to all possible index implementations")
  @ApiStatus.ScheduledForRemoval
  suspend fun indexingSaved(project: Project?, forActions: Boolean, durationMs: Long) {
    INDEXING_SAVED.log(project, getCommonFields(project, forActions) + listOf(EventFields.DurationMs.with(durationMs)))
  }

  private suspend fun getCommonFields(project: Project?, forActions: Boolean): List<EventPair<*>> {
    require(forActions || project != null)
    return buildList {
      add(MODEL_VERSION.with(Registry.stringValue("intellij.platform.ml.embeddings.model.version")))
      add(INDEX_TYPE.with(if (forActions) "actions" else "file-based"))
      add(ENABLED_INDICES.with(getEnabledIndices()))
      add(VECTOR_COUNT.with(getTotalSize(project, forActions)))
      add(USED_MEMORY_MB.with(getTotalUsedMemory(project, forActions)))
    }
  }

  private fun getEnabledIndices(): List<String> {
    return buildList {
      for (index in IndexId.entries.filter { it.isEnabled() }) {
        add(index.name.lowercase())
      }
    }
  }

  private suspend fun getTotalSize(project: Project?, forActions: Boolean): Long {
    return if (forActions) getStorageManagerWrapper(IndexId.ACTIONS).getStorageStats(null).size.toLong()
    else FILE_BASED_INDICES.sumOf { getStorageManagerWrapper(it).getStorageStats(project!!).size.toLong() }
  }

  private suspend fun getTotalUsedMemory(project: Project?, forActions: Boolean): Double {
    val totalBytes = if (forActions) getStorageManagerWrapper(IndexId.ACTIONS).getStorageStats(null).bytes
    else FILE_BASED_INDICES.sumOf { getStorageManagerWrapper(it).getStorageStats(project!!).bytes }
    return roundDouble(totalBytes.toDouble() / BYTES_IN_MEGABYTE)
  }

  private fun roundDouble(value: Double): Double {
    if (!value.isFinite()) return -1.0
    return round(value * 10000) / 10000
  }
}