// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic

import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import com.intellij.diagnostic.hprof.action.AnalysisRunnable
import com.intellij.diagnostic.hprof.action.getHeapDumpReportText
import com.intellij.diagnostic.report.HeapReportProperties
import com.intellij.diagnostic.report.MemoryReportReason
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Attachment
import com.intellij.openapi.diagnostic.ErrorReportSubmitter
import com.intellij.openapi.diagnostic.IdeaLoggingEvent
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import java.awt.Component
import java.nio.file.Path
import kotlin.io.path.*

internal open class HeapDumpAnalysisSupport {
  companion object {
    fun getInstance(): HeapDumpAnalysisSupport = service<HeapDumpAnalysisSupport>()
  }

  open fun getPrivacyPolicyUrl(): String = "https://www.jetbrains.com/company/privacy.html"

  open fun uploadReport(reportText: String, heapReportProperties: HeapReportProperties, parentComponent: Component) {
    val text = getHeapDumpReportText(reportText, heapReportProperties)
    val attachment = Attachment("report.txt", text).apply { isIncluded = true }
    attachment.isIncluded = true
    val event = IdeaLoggingEvent("Heap analysis results", OutOfMemoryError(), listOf(attachment), null, null)
    ErrorReportSubmitter.EP_NAME.findExtension(ITNReporter::class.java)?.submit(arrayOf(event), null, parentComponent) { }
  }

  /**
   * Checks if there's already a snapshot saved for analysis after restart and notifies the user if needed.
   * Returns true if there's a pending snapshot and a new one shouldn't be saved.
   */
  open fun checkPendingSnapshot(): Boolean = false

  /**
   * Saves the given snapshot for analysis after restart.
   */
  open fun saveSnapshotForAnalysis(hprofPath: Path, reportProperties: HeapReportProperties) {
    val jsonPath = Path.of(PathManager.getSystemPath(), "pending-snapshot.json")
    JsonWriter(jsonPath.bufferedWriter()).use {
      it.beginObject()
      it.name("path").value(hprofPath.toString())
      it.name("reason").value(reportProperties.reason.toString())
      it.name("liveStats").value(reportProperties.liveStats)
      it.endObject()
    }
  }

  open fun analysisFailed(heapProperties: HeapReportProperties) { }

  open fun analysisComplete(heapProperties: HeapReportProperties) { }
}

internal class AnalyzePendingSnapshotActivity: ProjectActivity {
  init {
    if (ApplicationManager.getApplication().isHeadlessEnvironment) {
      throw ExtensionNotApplicableException.create()
    }
  }

  override suspend fun execute(project: Project) {
    val jsonPath = Path.of(PathManager.getSystemPath(), "pending-snapshot.json")
    if (!jsonPath.isRegularFile()) {
      return
    }

    var path: String? = null
    var liveStats: String? = null
    var reason: MemoryReportReason? = null
    try {
      val reader = JsonReader(jsonPath.bufferedReader())
      reader.use {
        it.beginObject()
        while (it.hasNext()) {
          when (it.nextName()) {
            "path" -> path = it.nextString()
            "reason" -> reason = MemoryReportReason.valueOf(it.nextString())
            "liveStats" -> liveStats = it.nextString()
          }
        }
        it.endObject()
      }

      jsonPath.deleteIfExists()
    }
    catch (_: Exception) { }

    path?.let {
      val hprofPath = Path.of(it)
      if (hprofPath.exists()) {
        val heapProperties = HeapReportProperties(reason ?: MemoryReportReason.None, liveStats ?: "")
        AnalysisRunnable(hprofPath, heapProperties, true).run()
      }
    }
  }
}
