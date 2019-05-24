// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic

import com.intellij.diagnostic.hprof.action.getHeapDumpReportText
import com.intellij.diagnostic.report.HeapReportProperties
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.diagnostic.Attachment
import java.awt.Component
import java.nio.file.Path

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
  }

  open fun analysisFailed(heapProperties: HeapReportProperties) {
  }

  open fun analysisComplete(heapProperties: HeapReportProperties) {
  }
}
