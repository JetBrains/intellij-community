/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInsight.daemon.impl

import com.android.tools.analytics.UsageTracker
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.EditorFileType
import com.google.wireless.android.sdk.stats.EditorFileType.GROOVY
import com.google.wireless.android.sdk.stats.EditorFileType.JAVA
import com.google.wireless.android.sdk.stats.EditorFileType.JSON
import com.google.wireless.android.sdk.stats.EditorFileType.KOTLIN
import com.google.wireless.android.sdk.stats.EditorFileType.KOTLIN_SCRIPT
import com.google.wireless.android.sdk.stats.EditorFileType.NATIVE
import com.google.wireless.android.sdk.stats.EditorFileType.PROPERTIES
import com.google.wireless.android.sdk.stats.EditorFileType.UNKNOWN
import com.google.wireless.android.sdk.stats.EditorFileType.XML
import com.google.wireless.android.sdk.stats.EditorHighlightingStats
import com.intellij.codeInsight.daemon.impl.HighlightingStats.reportHighlightingStats
import com.intellij.concurrency.JobScheduler
import com.intellij.openapi.components.BaseComponent
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.analytics.toProto
import org.HdrHistogram.Recorder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Tracks highlighting latency across file types.
 * To log an [AndroidStudioEvent] with the collected data, call [reportHighlightingStats].
 */
object HighlightingStats : BaseComponent {
  private const val MAX_LATENCY_MS = 10 * 60 * 1000 // Limit latencies to 10 minutes to ensure reasonable histogram size.

  override fun initComponent() {
    // Send reports hourly.
    JobScheduler.getScheduler().scheduleWithFixedDelay(this::reportHighlightingStats, 1, 1, TimeUnit.HOURS)
  }

  /**
   * Maps file types to latency recorders.
   * We use [Recorder] to allow thread-safe read access from background threads.
   */
  private val latencyRecorders = ConcurrentHashMap<EditorFileType, Recorder>()

  fun recordHighlightingLatency(document: Document, latencyMs: Long) {
    if (latencyMs < 0 || latencyMs > MAX_LATENCY_MS) return
    val file = FileDocumentManager.getInstance().getFile(document) ?: return
    val fileType = convertFileType(file)
    val recorder = latencyRecorders.computeIfAbsent(fileType) { Recorder(1) }
    recorder.recordValue(latencyMs)
  }

  /**
   * Logs an [AndroidStudioEvent] with editor highlighting stats.
   * Resets statistics so that counts are not double-counted in the next report.
   */
  fun reportHighlightingStats() {
    val allStats = EditorHighlightingStats.newBuilder()
    for ((fileType, recorder) in latencyRecorders) {
      val histogram = recorder.intervalHistogram // Automatically resets statistics for this recorder.
      if (histogram.totalCount == 0L) {
        continue
      }
      val record = EditorHighlightingStats.Stats.newBuilder().also {
        it.fileType = fileType
        it.histogram = histogram.toProto()
      }
      allStats.addByFileType(record.build())
    }

    if (allStats.byFileTypeCount == 0) {
      return
    }

    UsageTracker.log(
      AndroidStudioEvent.newBuilder().apply {
        kind = AndroidStudioEvent.EventKind.EDITOR_HIGHLIGHTING_STATS
        editorHighlightingStats = allStats.build()
      }
    )
  }

  /** Converts from file type name to proto enum value. */
  private fun convertFileType(file: VirtualFile): EditorFileType = when (file.fileType.name) {
    // We use string literals here (rather than, e.g., JsonFileType.INSTANCE.name) to avoid unnecessary
    // dependencies on other plugins. Fortunately, these values are extremely unlikely to change.
    "JAVA" -> JAVA
    "Kotlin" -> if (file.extension == "kts") KOTLIN_SCRIPT else KOTLIN
    "XML" -> XML
    "Groovy" -> GROOVY
    "Properties" -> PROPERTIES
    "JSON" -> JSON
    "ObjectiveC" -> NATIVE
    else -> UNKNOWN
  }
}
