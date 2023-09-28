// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl

import com.intellij.codeHighlighting.Pass
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.diagnostic.Activity
import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.diagnostic.StartUpPerformanceService
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.*
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.ex.EditorMarkupModel
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.UserDataHolderEx
import com.intellij.psi.PsiDocumentManager
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.log10
import kotlin.math.pow

open class DaemonFusReporter(private val project: Project) : DaemonCodeAnalyzer.DaemonListener {
  private companion object {
    private val docPreviousAnalysisStatus = Key.create<AnalysisStatus>("docPreviousAnalysisStatus")
    private val sessionSegmentsTotalDurationMs = Key.create<AtomicLong>("sessionSegmentsTotalDurationMs")
  }

  private data class AnalysisStatus(val stamp: Long, val isDumbMode: Boolean)

  private data class SessionData(val daemonStartTime: Long = -1L,
                                 val dirtyRange: TextRange? = null,
                                 val documentStartedHash: Int = 0,
                                 val isDumbMode: Boolean = false)

  private var initialEntireFileHighlightingActivity: Activity? = null
  private var initialEntireFileHighlightingReported: Boolean = false

  private val currentSessionSegments = ConcurrentHashMap<FileEditor, SessionData>()

  override fun daemonStarting(fileEditors: Collection<FileEditor>) {
    val fileEditor = fileEditors.asSequence().filterIsInstance<TextEditor>().firstOrNull() ?: return
    val editor = fileEditor.editor
    if (editor.editorKind != EditorKind.MAIN_EDITOR && !ApplicationManager.getApplication().isUnitTestMode) {
      return
    }

    val document = editor.document
    val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document)!!
    val dirtyRange = FileStatusMap.getDirtyTextRange(document, psiFile, Pass.UPDATE_ALL)

    currentSessionSegments.put(fileEditor, SessionData(
      daemonStartTime = System.currentTimeMillis(),
      dirtyRange = dirtyRange,
      documentStartedHash = document.hashCode(),
      isDumbMode = DumbService.isDumb(project) // it's important to check for dumb mode here because state can change to opposite in daemonFinished
    ))

    if (!initialEntireFileHighlightingReported) {
      initialEntireFileHighlightingActivity = StartUpMeasurer.startActivity("initial entire file highlighting")
    }

    (editor as UserDataHolderEx).putUserDataIfAbsent(sessionSegmentsTotalDurationMs, AtomicLong())
  }

  override fun daemonCanceled(reason: String, fileEditors: Collection<FileEditor>) {
    daemonStopped(fileEditors, true)
  }

  override fun daemonFinished(fileEditors: Collection<FileEditor>) {
    daemonStopped(fileEditors, false)
  }

  private fun daemonStopped(fileEditors: Collection<FileEditor>, canceled: Boolean) {
    val fileEditor = fileEditors.filterIsInstance<TextEditor>().firstOrNull() ?: return
    val editor = fileEditor.editor
    if (editor.editorKind != EditorKind.MAIN_EDITOR && !ApplicationManager.getApplication().isUnitTestMode) {
      return
    }

    val document = editor.document
    val sessionData = currentSessionSegments.remove(fileEditor)!!
    if (sessionData.documentStartedHash != document.hashCode()) {
      editor.putUserData(sessionSegmentsTotalDurationMs, AtomicLong())
      // unmatched starting/finished events? bail out just in case
      return
    }

    if (editor.getUserData(docPreviousAnalysisStatus) == AnalysisStatus(document.modificationStamp, sessionData.isDumbMode)) {
      editor.putUserData(sessionSegmentsTotalDurationMs, AtomicLong())
      // Don't report 'finished' event in case of no changes in the document and dumb mode status was not changed
      // since such sessions are always fast to perform.
      return
    }

    val analyzer = (editor.markupModel as? EditorMarkupModel)?.errorStripeRenderer as? TrafficLightRenderer
    val errorCounts = analyzer?.errorCounts
    val registrar = SeverityRegistrar.getSeverityRegistrar(project)
    val errorIndex = registrar.getSeverityIdx(HighlightSeverity.ERROR)
    val warningIndex = registrar.getSeverityIdx(HighlightSeverity.WARNING)
    val errorCount = errorCounts?.getOrNull(errorIndex) ?: -1
    val warningCount = errorCounts?.getOrNull(warningIndex) ?: -1
    val lines = document.lineCount.roundToOneSignificantDigit()
    val segmentElapsedTime = System.currentTimeMillis() - sessionData.daemonStartTime
    val fileType = document.let { FileDocumentManager.getInstance().getFile(it)?.fileType }
    val highlightingCompleted = DaemonCodeAnalyzerImpl.isHighlightingCompleted(fileEditor, project)

    if (highlightingCompleted && !canceled && !initialEntireFileHighlightingReported) {
      initialEntireFileHighlightingReported = true
      initialEntireFileHighlightingActivity!!.end()
      initialEntireFileHighlightingActivity = null
      StartUpMeasurer.addInstantEvent("editor highlighting completed")
      @Suppress("DEPRECATION")
      project.coroutineScope.launch {
        StartUpPerformanceService.getInstance().editorRestoringTillHighlighted()
      }
    }

    val previousSegmentsTotalDuration = editor.getUserData(sessionSegmentsTotalDurationMs)!!
    val currentSegmentsTotalDuration = previousSegmentsTotalDuration.addAndGet(segmentElapsedTime)

    DaemonFusCollector.FINISHED.log(
      project,
      DaemonFusCollector.SEGMENT_DURATION with segmentElapsedTime,
      DaemonFusCollector.FULL_DURATION with currentSegmentsTotalDuration,
      DaemonFusCollector.ERRORS with errorCount,
      DaemonFusCollector.WARNINGS with warningCount,
      DaemonFusCollector.LINES with lines,
      EventFields.FileType with fileType,
      DaemonFusCollector.HIGHLIGHTING_COMPLETED with highlightingCompleted,
      DaemonFusCollector.DUMB_MODE with sessionData.isDumbMode,
    )

    if (highlightingCompleted) {
      editor.putUserData(docPreviousAnalysisStatus, AnalysisStatus(document.modificationStamp, sessionData.isDumbMode))
      editor.putUserData(sessionSegmentsTotalDurationMs, AtomicLong())
    }
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
  val GROUP: EventLogGroup = EventLogGroup("daemon", 9)
  @JvmField
  val ERRORS: IntEventField = EventFields.Int("errors")
  @JvmField
  val WARNINGS: IntEventField = EventFields.Int("warnings")
  @JvmField
  val LINES: IntEventField = EventFields.Int("lines")

  @JvmField
  val HIGHLIGHTING_COMPLETED: BooleanEventField = EventFields.Boolean("highlighting_completed")

  @JvmField
  val DUMB_MODE: BooleanEventField = EventFields.Boolean("dumb_mode")

  /**
   * Daemon highlighting segment duration until it was finished or restarted for some reason.
   */
  @JvmField
  val SEGMENT_DURATION: LongEventField = LongEventField("segment_duration_ms")

  /**
   * Full highlighting duration since the file was modified and/or dumb mode status changed. It should be equal to the sum of segments.
   */
  @JvmField
  val FULL_DURATION: LongEventField = LongEventField("full_duration_since_started_ms")

  @JvmField
  val FINISHED: VarargEventId = GROUP.registerVarargEvent("finished",
                                                          SEGMENT_DURATION,
                                                          FULL_DURATION,
                                                          ERRORS,
                                                          WARNINGS,
                                                          LINES,
                                                          EventFields.FileType,
                                                          HIGHLIGHTING_COMPLETED,
                                                          DUMB_MODE)

  override fun getGroup(): EventLogGroup = GROUP
}
