// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl

import com.intellij.codeHighlighting.Pass
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.diagnostic.Activity
import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.diagnostic.StartUpPerformanceService
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.BooleanEventField
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.IntEventField
import com.intellij.internal.statistic.eventLog.events.VarargEventId
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.ex.EditorMarkupModel
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import kotlinx.coroutines.launch
import kotlin.math.log10
import kotlin.math.pow

open class DaemonFusReporter(private val project: Project) : DaemonCodeAnalyzer.DaemonListener {
  @Volatile
  private var daemonStartTime: Long = -1L

  @Volatile
  private var dirtyRange: TextRange? = null
  private var initialEntireFileHighlightingActivity: Activity? = null
  private var initialEntireFileHighlightingReported: Boolean = false
  private var docPreviousAnalyzedModificationStamp: Long = 0
  private var documentStartedHash: Int = 0 // Document the `daemonStarting` event was received for. Store the hash instead of Document instance to avoid leaks

  @Volatile
  protected var canceled: Boolean = false

  private var isDumbMode: Boolean = false

  override fun daemonStarting(fileEditors: Collection<FileEditor>) {
    // it's important to check for dumb mode here because state can change to opposite in daemonFinished
    isDumbMode = DumbService.isDumb(project)
    canceled = false
    daemonStartTime = System.currentTimeMillis()
    val editor = fileEditors.asSequence().filterIsInstance<TextEditor>().firstOrNull()?.editor
    val document = editor?.document
    dirtyRange = if (document == null) {
      null
    }
    else {
      val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document)
      if (psiFile == null) null else FileStatusMap.getDirtyTextRange(document, psiFile, Pass.UPDATE_ALL)
    }
    documentStartedHash = document?.hashCode() ?: 0

    if (!initialEntireFileHighlightingReported) {
      initialEntireFileHighlightingActivity = StartUpMeasurer.startActivity("initial entire file highlighting")
    }
  }

  override fun daemonCancelEventOccurred(reason: String) {
    canceled = true
  }

  override fun daemonFinished(fileEditors: Collection<FileEditor>) {
    val fileEditor = fileEditors.filterIsInstance<TextEditor>().firstOrNull()
    val editor = fileEditor?.editor
    val document = editor?.document
    if (document != null && documentStartedHash != document.hashCode()) {
      // unmatched starting/finished events? bail out just in case
      return
    }

    if (document != null && docPreviousAnalyzedModificationStamp == document.modificationStamp && initialEntireFileHighlightingReported) {
      // Don't report 'finished' event in case of no changes in the document
      return
    }

    val analyzer = (editor?.markupModel as? EditorMarkupModel)?.errorStripeRenderer as? TrafficLightRenderer
    val errorCounts = analyzer?.errorCounts
    val registrar = SeverityRegistrar.getSeverityRegistrar(project)
    val errorIndex = registrar.getSeverityIdx(HighlightSeverity.ERROR)
    val warningIndex = registrar.getSeverityIdx(HighlightSeverity.WARNING)
    val errorCount = errorCounts?.getOrNull(errorIndex) ?: -1
    val warningCount = errorCounts?.getOrNull(warningIndex) ?: -1
    val lines = document?.lineCount?.roundToOneSignificantDigit() ?: -1
    val elapsedTime = System.currentTimeMillis() - daemonStartTime
    val fileType = document?.let { FileDocumentManager.getInstance().getFile(it)?.fileType }
    val wasEntireFileHighlighted = document != null && DaemonCodeAnalyzerImpl.isHighlightingCompleted(fileEditor, project);

    if (wasEntireFileHighlighted && !initialEntireFileHighlightingReported) {
      initialEntireFileHighlightingReported = true
      initialEntireFileHighlightingActivity!!.end()
      initialEntireFileHighlightingActivity = null
      StartUpMeasurer.addInstantEvent("editor highlighting completed")
      @Suppress("DEPRECATION")
      project.coroutineScope.launch {
        StartUpPerformanceService.getInstance().editorRestoringTillHighlighted()
      }
    }

    DaemonFusCollector.FINISHED.log(
      project,
      EventFields.DurationMs with elapsedTime,
      DaemonFusCollector.ERRORS with errorCount,
      DaemonFusCollector.WARNINGS with warningCount,
      DaemonFusCollector.LINES with lines,
      EventFields.FileType with fileType,
      DaemonFusCollector.ENTIRE_FILE_HIGHLIGHTED with wasEntireFileHighlighted,
      DaemonFusCollector.CANCELED with canceled,
      DaemonFusCollector.DUMB_MODE with isDumbMode,
    )
    docPreviousAnalyzedModificationStamp = document?.modificationStamp ?: 0
  }
}

private fun Int.roundToOneSignificantDigit(): Int {
  if (this == 0) return 0
  val l = log10(toDouble()).toInt()          // 623 -> 2
  val p = 10.0.pow(l.toDouble()).toInt()     // 623 -> 100
  return (this - this % p).coerceAtLeast(10) // 623 -> 623 - (623 % 100) = 600
}

private object DaemonFusCollector : CounterUsagesCollector() {
  @JvmField
  val GROUP: EventLogGroup = EventLogGroup("daemon", 6)
  @JvmField
  val ERRORS: IntEventField = EventFields.Int("errors")
  @JvmField
  val WARNINGS: IntEventField = EventFields.Int("warnings")
  @JvmField
  val LINES: IntEventField = EventFields.Int("lines")
  @JvmField
    /**
     * `true` if the daemon was started with the entire file range,
     * `false` when the daemon was started with sub-range of the file, for example, after the change inside a code block
     */
  val ENTIRE_FILE_HIGHLIGHTED: BooleanEventField = EventFields.Boolean("entireFileHighlighted")

  @JvmField
    /**
     * `true` if the daemon was finished because of some cancellation event (e.g., user tried to type something into the editor).
     * Usually it means the highlighting results are incomplete.
     */
  val CANCELED: BooleanEventField = EventFields.Boolean("canceled")

  @JvmField
  val DUMB_MODE: BooleanEventField = EventFields.Boolean("dumb_mode")

  @JvmField
  val FINISHED: VarargEventId = GROUP.registerVarargEvent("finished",
                                                          EventFields.DurationMs,
                                                          ERRORS,
                                                          WARNINGS,
                                                          LINES,
                                                          EventFields.FileType,
                                                          ENTIRE_FILE_HIGHLIGHTED,
                                                          CANCELED,
                                                          DUMB_MODE)

  override fun getGroup(): EventLogGroup = GROUP
}
