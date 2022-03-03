// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.intellij.util.io.exists
import java.awt.Component
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

open class HeapDumpAnalysisSupport {
  companion object {
    fun getInstance() = service<HeapDumpAnalysisSupport>()
  }

  open fun getPrivacyPolicyUrl(): String {
    return "https://www.jetbrains.com/company/privacy.html"
  }

  open fun uploadReport(reportText: String, heapReportProperties: HeapReportProperties, parentComponent: Component) {
    val text = getHeapDumpReportText(reportText, heapReportProperties)
    val attachment = Attachment("report.txt", text)
    attachment.isIncluded = true
    val loggingEvent = LogMessage.createEvent(OutOfMemoryError(), "Heap analysis results", attachment)
    ITNReporter().submit(arrayOf(loggingEvent), null, parentComponent) { }
  }

  /**
   * Checks if there's already a snapshot saved for analysis after restart and notifies the user if needed.
   * Returns true if there's a pending snapshot and a new one should not be saved.
   */
  open fun checkPendingSnapshot(): Boolean {
    return false
  }

  /**
   * Saves the given snapshot for analysis after restart.
   */
  open fun saveSnapshotForAnalysis(hprofPath: Path, reportProperties: HeapReportProperties) {
    val jsonPath = File(PathManager.getSystemPath(), "pending-snapshot.json")
    JsonWriter(OutputStreamWriter(FileOutputStream(jsonPath))).use {
      it.beginObject()
      it.name("path").value(hprofPath.toString())
      it.name("reason").value(reportProperties.reason.toString())
      it.name("liveStats").value(reportProperties.liveStats)
      it.endObject()
    }
  }

  open fun analysisFailed(heapProperties: HeapReportProperties) {
  }

  open fun analysisComplete(heapProperties: HeapReportProperties) {
  }
}

internal class AnalyzePendingSnapshotActivity: StartupActivity.DumbAware {
  override fun runActivity(project: Project) {
    if (ApplicationManager.getApplication().isHeadlessEnvironment) {
      return
    }

    val jsonPath = Path.of(PathManager.getSystemPath(), "pending-snapshot.json")
    if (!Files.isRegularFile(jsonPath)) {
      return
    }

    var path: String? = null
    var liveStats: String? = null
    var reason: MemoryReportReason? = null
    try {
      val reader = JsonReader(Files.newBufferedReader(jsonPath))
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

      Files.deleteIfExists(jsonPath)
    }
    catch (ignore: Exception) {
    }

    path?.let {
      val hprofPath = Paths.get(it)
      if (hprofPath.exists()) {
        val heapProperties = HeapReportProperties(reason ?: MemoryReportReason.None, liveStats ?: "")
        AnalysisRunnable(hprofPath, heapProperties, true).run()
      }
    }
  }
}