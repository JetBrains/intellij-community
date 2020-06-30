/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.intellij.diagnostic.hprof.action

import com.intellij.diagnostic.DiagnosticBundle
import com.intellij.diagnostic.HeapDumpAnalysisSupport
import com.intellij.diagnostic.hprof.analysis.HProfAnalysis
import com.intellij.diagnostic.hprof.analysis.analyzeGraph
import com.intellij.diagnostic.hprof.util.HeapDumpAnalysisNotificationGroup
import com.intellij.diagnostic.report.HeapReportProperties
import com.intellij.ide.BrowserUtil
import com.intellij.ide.actions.RevealFileAction
import com.intellij.ide.actions.ShowLogAction
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.impl.ApplicationInfoImpl
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.wm.WindowManager
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.SwingHelper
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.io.File
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.OpenOption
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.concurrent.CompletableFuture
import javax.swing.*
import javax.swing.event.HyperlinkEvent

class AnalysisRunnable(val hprofPath: Path,
                       val heapProperties: HeapReportProperties,
                       private val deleteAfterAnalysis: Boolean) : Runnable {

  companion object {
    private val LOG = Logger.getInstance(AnalysisRunnable::class.java)
  }

  override fun run() {
    AnalysisTask().queue()
  }

  inner class AnalysisTask : Task.Backgroundable(null, DiagnosticBundle.message("heap.dump.analysis.task.title"), false) {

    override fun onThrowable(error: Throwable) {
      LOG.error(error)

      HeapDumpAnalysisSupport.getInstance().analysisFailed(heapProperties)

      val notification = HeapDumpAnalysisNotificationGroup.GROUP.createNotification(DiagnosticBundle.message("heap.dump.analysis.exception"),
                                                                                    NotificationType.INFORMATION)
      notification.addAction(NotificationAction.createSimpleExpiring(ShowLogAction.getActionName()) {
        RevealFileAction.openFile(File(PathManager.getLogPath(), "idea.log"))
      })
      notification.notify(null)
      if (deleteAfterAnalysis) {
        deleteHprofFileAsync()
      }
    }

    private fun deleteHprofFileAsync() {
      CompletableFuture.runAsync { Files.deleteIfExists(hprofPath) }
    }

    override fun run(indicator: ProgressIndicator) {
      indicator.isIndeterminate = false
      indicator.text = "Analyze Heap"
      indicator.fraction = 0.0

      val openOptions: Set<OpenOption>
      if (deleteAfterAnalysis) {
        openOptions = setOf(StandardOpenOption.READ, StandardOpenOption.DELETE_ON_CLOSE)
      }
      else {
        openOptions = setOf(StandardOpenOption.READ)
      }
      val reportString = FileChannel.open(hprofPath, openOptions).use { channel ->
        HProfAnalysis(channel, SystemTempFilenameSupplier(), ::analyzeGraph).analyze(indicator)
      }
      if (deleteAfterAnalysis) {
        deleteHprofFileAsync()
      }

      val notification = HeapDumpAnalysisNotificationGroup.GROUP.createNotification(
        title = DiagnosticBundle.message("heap.dump.analysis.notification.title"),
        content = DiagnosticBundle.message("heap.dump.analysis.notification.ready.content"),
        type = NotificationType.INFORMATION)
      notification.isImportant = true
      notification.addAction(ReviewReportAction(reportString, heapProperties))

      notification.notify(null)
    }
  }

  class ReviewReportAction(private val reportText: String, private val heapProperties: HeapReportProperties) :
    NotificationAction(DiagnosticBundle.message("heap.dump.analysis.notification.action.title")) {
    private var reportShown = false

    override fun actionPerformed(e: AnActionEvent, notification: Notification) {
      val parentComponent = WindowManager.getInstance().getFrame(e.project) ?: return
      if (reportShown) {
        return
      }

      reportShown = true
      UIUtil.invokeLaterIfNeeded {
        notification.expire()

        val reportDialog = ShowReportDialog(reportText, heapProperties)
        val userAgreedToSendReport = reportDialog.showAndGet()

        HeapDumpAnalysisSupport.getInstance().analysisComplete(heapProperties)

        if (userAgreedToSendReport) {
          HeapDumpAnalysisSupport.getInstance().uploadReport(reportText, heapProperties, parentComponent)
        }
      }
    }
  }
}
private const val SECTION_SEPARATOR = "================"

fun getHeapDumpReportText(reportText: String, heapProperties: HeapReportProperties): String {
  return "${reportText}${SECTION_SEPARATOR}\n${heapProperties.liveStats}"
}

class ShowReportDialog(reportText: String, heapProperties: HeapReportProperties) : DialogWrapper(false) {
  private val textArea: JTextArea = JTextArea(30, 130)

  init {
    textArea.text = getHeapDumpReportText(reportText, heapProperties)
    textArea.isEditable = false
    textArea.caretPosition = 0
    init()
    title = DiagnosticBundle.message("heap.dump.analysis.report.dialog.title")
    isModal = true
  }

  override fun createCenterPanel(): JComponent? {
    val pane = JPanel(BorderLayout(0, 5))
    val productName = ApplicationNamesInfo.getInstance().fullProductName
    val vendorName = ApplicationInfoImpl.getShadowInstance().shortCompanyName

    val header = JLabel(DiagnosticBundle.message("heap.dump.analysis.report.dialog.header", productName, vendorName))

    pane.add(header, BorderLayout.PAGE_START)
    pane.add(JBScrollPane(textArea), BorderLayout.CENTER)
    with(SwingHelper.createHtmlViewer(true, null, JBColor.WHITE, JBColor.BLACK)) {
      isOpaque = false
      isFocusable = false
      addHyperlinkListener {
        if (it.eventType == HyperlinkEvent.EventType.ACTIVATED) {
          it.url?.let(BrowserUtil::browse)
        }
      }
      text = DiagnosticBundle.message("heap.dump.analysis.report.dialog.footer",
                                      ApplicationInfo.getInstance().shortCompanyName, HeapDumpAnalysisSupport.getInstance().getPrivacyPolicyUrl())
      pane.add(this, BorderLayout.PAGE_END)
    }

    return pane
  }

  override fun createActions(): Array<Action> {
    return arrayOf(okAction, cancelAction)
  }

  override fun createDefaultActions() {
    super.createDefaultActions()
    okAction.putValue(Action.NAME, DiagnosticBundle.message("heap.dump.analysis.report.dialog.action.send"))
    cancelAction.putValue(Action.NAME, DiagnosticBundle.message("heap.dump.analysis.report.dialog.action.dont.send"))
  }
}

class SystemTempFilenameSupplier : HProfAnalysis.TempFilenameSupplier {
  override fun getTempFilePath(type: String): Path {
    return FileUtil.createTempFile("studio-analysis-", "-$type.tmp").toPath()
  }
}
