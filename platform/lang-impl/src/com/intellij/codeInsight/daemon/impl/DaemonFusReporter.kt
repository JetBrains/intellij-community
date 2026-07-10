// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.FeatureUsageData
import com.intellij.internal.statistic.eventLog.events.BooleanEventField
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.IntEventField
import com.intellij.internal.statistic.eventLog.events.LongEventField
import com.intellij.internal.statistic.eventLog.events.PrimitiveEventField
import com.intellij.internal.statistic.eventLog.events.VarargEventId
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
import com.intellij.openapi.vfs.VirtualFileWithId
import com.intellij.util.containers.ContainerUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import org.jetbrains.annotations.TestOnly
import kotlin.math.log10
import kotlin.math.pow

internal class DaemonFusReporter(private val project: Project, val coroutineScope: CoroutineScope) : DaemonCodeAnalyzer.DaemonListener {
  private val sessions = ContainerUtil.createConcurrentWeakMap<FileEditor, SessionData>()

  private data class SessionData(
    val daemonStartTime: Long = -1L,
    val documentStartedHash: Int = 0,
    val isDumbMode: Boolean = false,
    var sessionSegmentTotalDurationMs: Long = 0,
    var docPreviousStamp: Long
  )

  init {
    coroutineScope.launch {
      fusEvents.collect {
        // skip nulls emitted by drain()/by DaemonFusReporter from the other opened project
        if (it != null && it.project == project) {
          report(it.project, it.fileEditor, it.sessionData)
        }
      }
    }
  }

  override fun daemonStarting(fileEditors: Collection<FileEditor>) {
    val fileEditor = fileEditors.asSequence().filterIsInstance<TextEditor>().firstOrNull() ?: return
    val editor = fileEditor.editor
    if (editor.editorKind != EditorKind.MAIN_EDITOR && !ApplicationManager.getApplication().isUnitTestMode) {
      return
    }

    val document = editor.document

    sessions.compute(fileEditor) { _, oldData -> SessionData(
      daemonStartTime = System.currentTimeMillis(),
      documentStartedHash = document.hashCode(),
      isDumbMode = DumbService.isDumb(project), // it's important to check for dumb mode here because state can change to opposite in daemonFinished
      docPreviousStamp = oldData?.docPreviousStamp?:-1
    )}
  }

  override fun daemonCanceled(reason: String, fileEditors: Collection<FileEditor>) {
    daemonStopped(fileEditors)
  }

  override fun daemonFinished(fileEditors: Collection<FileEditor>) {
    daemonStopped(fileEditors)
  }

  private fun daemonStopped(fileEditors: Collection<FileEditor>) {
    val fileEditor = fileEditors.filterIsInstance<TextEditor>().firstOrNull() ?: return
    val editor = fileEditor.editor
    if (editor.editorKind != EditorKind.MAIN_EDITOR && !ApplicationManager.getApplication().isUnitTestMode) {
      return
    }

    val document = editor.document
    val sessionData = sessions[fileEditor] ?: return
    if (sessionData.documentStartedHash != document.hashCode()) {
      sessionData.sessionSegmentTotalDurationMs = 0
      // unmatched starting/finished events? bail out just in case
      return
    }

    if (sessionData.docPreviousStamp == document.modificationStamp && sessionData.isDumbMode == DumbService.isDumb(project)) {
      sessionData.sessionSegmentTotalDurationMs = 0
      // Don't report 'finished' event in case of no changes in the document and dumb mode status was not changed
      // since such sessions are always fast to perform.
      return
    }

    fusEvents.tryEmit(FUSData(project, fileEditor, sessionData))
  }

  companion object {
    private data class FUSData(val project: Project, val fileEditor: TextEditor, val sessionData: SessionData)
    private val fusEvents: MutableSharedFlow<FUSData?> = MutableSharedFlow(extraBufferCapacity = 1000, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    /**
     * makes sure all events emitted to [fusEvents] are processed by [report]
     */
    @TestOnly
    fun drain() {
      assert(ApplicationManager.getApplication().isUnitTestMode)
      // if this (null) element is removed from the flow, it means the previous was processed (doReport() finished for it),
      // so to make sure all events are reported, it's enough to wait for this null element to be just removed from the flow, not necessarily processed
      fusEvents.tryEmit(null)

      while (!fusEvents.replayCache.isEmpty()) {
        Thread.yield()
      }
    }

    private fun roundToOneSignificantDigit(lines: Int): Int {
      if (lines == 0) return 0
      val l = log10(lines.toDouble()).toInt()          // 623 -> 2
      val p = 10.0.pow(l.toDouble()).toInt()     // 623 -> 100
      return (lines - lines % p).coerceAtLeast(10) // 623 -> 623 - (623 % 100) = 600
    }

    private fun report(project: Project, fileEditor: TextEditor, sessionData: SessionData) {
      if (!fileEditor.isValid || project.isDisposed) return
      val document = fileEditor.editor.document
      val analyzer = (fileEditor.editor.markupModel as? EditorMarkupModel)?.errorStripeRenderer as? TrafficLightRenderer
      val errorCounts = analyzer?.errorCountsForFus
      val registrar = SeverityRegistrar.getSeverityRegistrar(project)
      val errorIndex = registrar.getSeverityIdx(HighlightSeverity.ERROR)
      val warningIndex = registrar.getSeverityIdx(HighlightSeverity.WARNING)
      val errorCount = errorCounts?.getOrNull(errorIndex) ?: -1
      val warningCount = errorCounts?.getOrNull(warningIndex) ?: -1
      val lines = roundToOneSignificantDigit(document.lineCount)
      val segmentElapsedTime = System.currentTimeMillis() - sessionData.daemonStartTime
      val virtualFile = FileDocumentManager.getInstance().getFile(document)
      val fileType = virtualFile?.fileType
      val highlightingCompleted = DaemonCodeAnalyzerImpl.isHighlightingCompleted(fileEditor, project)

      sessionData.sessionSegmentTotalDurationMs += segmentElapsedTime

      DaemonFusCollector.FINISHED.log(
        project,
        DaemonFusCollector.SEGMENT_DURATION with segmentElapsedTime,
        DaemonFusCollector.FULL_DURATION with sessionData.sessionSegmentTotalDurationMs,
        DaemonFusCollector.ERRORS with errorCount,
        DaemonFusCollector.WARNINGS with warningCount,
        DaemonFusCollector.LINES with lines,
        EventFields.FileType with fileType,
        DaemonFusCollector.HIGHLIGHTING_COMPLETED with highlightingCompleted,
        DaemonFusCollector.DUMB_MODE with sessionData.isDumbMode,
        DaemonFusCollector.FILE_ID with (virtualFile as? VirtualFileWithId)?.id
      )

      if (highlightingCompleted) {
        sessionData.docPreviousStamp = document.modificationStamp
        sessionData.sessionSegmentTotalDurationMs = 0
      }
    }
  }
}

internal object DaemonFusCollector : CounterUsagesCollector() {
  @JvmField
  val GROUP: EventLogGroup = EventLogGroup("daemon", 10)
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
  val FILE_ID: PrimitiveEventField<Int?> = object : PrimitiveEventField<Int?>() {
    override val name: String = "file_id"
    override val validationRule: List<String>
      get() = listOf("{regexp#integer}")

    override fun addData(fuData: FeatureUsageData, value: Int?) {
      value?.let { fuData.addData(name, it) }
    }
  }

  @JvmField
  val FINISHED: VarargEventId = GROUP.registerVarargEvent(
    "finished",
    SEGMENT_DURATION,
    FULL_DURATION,
    ERRORS,
    WARNINGS,
    LINES,
    EventFields.FileType,
    HIGHLIGHTING_COMPLETED,
    DUMB_MODE,
    FILE_ID,
  )

  override fun getGroup(): EventLogGroup = GROUP
}
