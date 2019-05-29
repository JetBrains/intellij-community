// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic

import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import com.intellij.diagnostic.hprof.action.AnalysisRunnable
import com.intellij.diagnostic.hprof.action.getHeapDumpReportText
import com.intellij.diagnostic.report.HeapReportProperties
import com.intellij.diagnostic.report.MemoryReportReason
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.diagnostic.Attachment
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.util.io.FileUtil
import java.awt.Component
import java.io.*
import java.nio.file.Path
import java.nio.file.Paths

/**
 * @author yole
 */
open class HeapDumpAnalysisSupport {
  companion object {
    fun getInstance(): HeapDumpAnalysisSupport = ServiceManager.getService(HeapDumpAnalysisSupport::class.java)
  }

  open fun getPrivacyPolicyUrl(): String {
    return "https://www.jetbrains.com/company/privacy.html";
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

class AnalyzePendingSnapshotActivity: StartupActivity {
  override fun runActivity(project: Project) {
    var path: String? = null
    var liveStats: String? = null
    var reason: MemoryReportReason? = null

    val jsonPath = File(PathManager.getSystemPath(), "pending-snapshot.json")
    if (jsonPath.exists()) {
      try {
        val reader = JsonReader(InputStreamReader(FileInputStream(jsonPath)))
        reader.use {
          it.beginObject()
          while (it.hasNext()) {
            val name = it.nextName()
            when (name) {
              "path" -> path = it.nextString()
              "reason" -> reason = MemoryReportReason.valueOf(it.nextString())
              "liveStats" -> liveStats = it.nextString()
            }
          }
          it.endObject()
        }

        FileUtil.delete(jsonPath)
      }
      catch (e: Exception) {
        // ignore
      }
    }

    path?.let {
      val heapProperties = HeapReportProperties(reason ?: MemoryReportReason.None, liveStats ?: "")
      AnalysisRunnable(Paths.get(it), heapProperties, true).run()
    }
  }
}