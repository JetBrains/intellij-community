// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.statistics

import com.intellij.debugger.actions.JvmSmartStepIntoHandler
import com.intellij.debugger.engine.AsyncStacksUtils
import com.intellij.debugger.engine.DebugProcess
import com.intellij.debugger.engine.DebugProcessEvents
import com.intellij.debugger.engine.SteppingAction
import com.intellij.debugger.impl.DebuggerUtilsImpl
import com.intellij.debugger.statistics.EvaluationOnPauseStatus.*
import com.intellij.debugger.ui.breakpoints.Breakpoint
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventFields.Int
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import org.jetbrains.annotations.ApiStatus


@ApiStatus.Internal
object DebuggerStatistics : CounterUsagesCollector() {
  override fun getGroup(): EventLogGroup = GROUP

  private val GROUP = EventLogGroup("java.debugger", 13)

  // fields

  /**
   * These are XBreakpointType IDs related to JVM languages.
   *
   * Use [XBreakpointType.EXTENSION_POINT_NAME.extensions.map { it.id }] to get the full list.
   */
  private val breakpointTypeField = EventFields.String("type", listOf(
    "java-exception", "java-collection", "java-wildcard-method",
    "java-line", "java-field", "java-method",
    "kotlin-line", "kotlin-field", "kotlin-function",
  ))

  private val steppingActionField = EventFields.Enum<SteppingAction>("step_action")
  private val languageField = EventFields.Enum<Engine>("language")

  private val dumpedCoroutinesCounter = Int("coroutines")
  private val dumpedVirtualThreadsCounter = Int("virtual_threads")

  private val threadDumpTriggeringExceptionField =
    EventFields.StringValidatedByCustomRule("exception", ThreadDumpTriggeringExceptionValidator::class.java)

  // events
  /** Reports overhead spent on checking where a breakpoint must be installed. */
  private val breakpointInstallationOverhead = GROUP.registerEvent("breakpoint.install.overhead", breakpointTypeField, EventFields.DurationMs)
  /** Reports overhead spent on searching for classes related to a breakpoint. */
  private val breakpointInstallSearchOverhead = GROUP.registerEvent("breakpoint.install.search.overhead", breakpointTypeField, EventFields.DurationMs)
  /** Reports overhead caused by debugger considering the necessity to stop on the breakpoint (condition breakpoints). */
  private val breakpointVisitOverhead = GROUP.registerEvent("breakpoint.visit.overhead", breakpointTypeField, EventFields.DurationMs)
  /** Reports overhead caused by debugger considering stepping procedure (smart step into, kotlin step out). */
  private val steppingOverhead = GROUP.registerEvent("stepping.overhead", steppingActionField, languageField, EventFields.DurationMs)
  /** Reports smart step into unexpected end. Could be caused by unexpected exception. */
  private val steppingFailedMethodNotCalled = GROUP.registerEvent("stepping.method.not.called", steppingActionField, languageField)
  /** Reports successful or failed targets detection in smart-step-into. */
  private val smartStepTargetsDetection = GROUP.registerEvent("smart.step.into.targets.detected", languageField, EventFields.Enum<JvmSmartStepIntoHandler.SmartStepIntoDetectionStatus>("status"))

  private val breakpointSkipped = GROUP.registerEvent("breakpoint.skipped", EventFields.Enum<DebugProcessEvents.SkippedBreakpointReason>("reason"))

  private val threadDump = GROUP.registerEvent("thread.dump", EventFields.Enum<ThreadDumpStatus>("status"), dumpedCoroutinesCounter, dumpedVirtualThreadsCounter)

  /** Reports successful or failed attempts to get evaluatable context on pause **/
  private val evaluationOnPause = GROUP.registerEvent("evaluation.on.pause", EventFields.Enum<EvaluationOnPauseStatus>("status"))

  private val threadDumpTriggeringException = GROUP.registerEvent("thread.dump.triggering.exception", threadDumpTriggeringExceptionField, EventFields.Count)

  /** Reports execution time of debugger commands in buckets, updated at the end of a debugger session. */
  private val timeBucketCount = GROUP.registerEvent("debugger.command.time.bucket.updated", EventFields.Int("bucket_upper_limit_ms"), EventFields.Count, EventFields.Boolean("is_remote"))
  internal val bucketUpperLimits = (generateSequence(1L) { it * 2 }.takeWhile { it <= 2048 } + Long.MAX_VALUE).toList().toLongArray()

  @JvmStatic
  fun logProcessStatistics(debugProcess: DebugProcess) {
    val collectedStats = StatisticsStorage.collectAndClearData(debugProcess)

    for ((key, timeMs) in collectedStats) {
      when (key) {
        is BreakpointInstallStatistic -> logBreakpointInstallOverhead(key.breakpoint, timeMs)
        is SteppingStatistic -> logSteppingOverhead(debugProcess.project, key, timeMs)
      }
    }

    val isRemote = DebuggerUtilsImpl.isRemote(debugProcess)
    val bucketCounts = StatisticsStorage.collectCommandsPerformance(debugProcess)
    val intUpperLimits = bucketUpperLimits.map { if (it == Long.MAX_VALUE) Int.MAX_VALUE else it.toInt() }.toIntArray()
    for ((upperLimitMs, count) in intUpperLimits.zip(bucketCounts)) {
      timeBucketCount.log(debugProcess.project, upperLimitMs, count, isRemote)
    }
  }

  @JvmStatic
  fun logBreakpointInstallOverhead(breakpoint: Breakpoint<*>, timeMs: Long) {
    val type = breakpoint.type ?: return
    breakpointInstallationOverhead.log(breakpoint.project, type, timeMs)
  }

  @JvmStatic
  fun logBreakpointInstallSearchOverhead(breakpoint: Breakpoint<*>, timeMs: Long) {
    val type = breakpoint.type ?: return
    breakpointInstallSearchOverhead.log(breakpoint.project, type, timeMs)
  }

  @JvmStatic
  fun logBreakpointVisit(breakpoint: Breakpoint<*>, timeMs: Long) {
    val type = breakpoint.type ?: return
    breakpointVisitOverhead.log(breakpoint.project, type, timeMs)
  }

  @JvmStatic
  fun logSteppingOverhead(project: Project, statistic: SteppingStatistic, timeMs: Long) {
    steppingOverhead.log(project, statistic.action, statistic.engine, timeMs)
  }

  @JvmStatic
  fun logMethodSkippedDuringStepping(project: Project, statistic: SteppingStatistic?) {
    if (statistic == null) return
    steppingFailedMethodNotCalled.log(project, statistic.action, statistic.engine)
  }

  @JvmStatic
  fun logBreakpointSkipped(project: Project, reason: DebugProcessEvents.SkippedBreakpointReason) {
    breakpointSkipped.log(project, reason)
  }

  @JvmStatic
  fun logSmartStepIntoTargetsDetection(project: Project?, language: Engine, status: JvmSmartStepIntoHandler.SmartStepIntoDetectionStatus) {
    smartStepTargetsDetection.log(project, language, status)
  }

  @JvmStatic
  fun logEvaluatablePauseSuccess(project: Project?, isDebuggerAgentAvailable: Boolean) =
    logEvaluatablePauseStatus(project, isDebuggerAgentAvailable, true)

  @JvmStatic
  fun logEvaluatablePauseFailure(project: Project?, isDebuggerAgentAvailable: Boolean) =
    logEvaluatablePauseStatus(project, isDebuggerAgentAvailable, false)

  @JvmStatic
  fun logEvaluatablePauseDisabled(project: Project?) {
    evaluationOnPause.log(project, EVALUATION_ON_PAUSE_DISABLED)
  }

  @JvmStatic
  fun logThreadDumpTriggerException(project: Project?, name: String) {
    threadDumpTriggeringException.log(project, name, 1)
  }

  private fun logEvaluatablePauseStatus(project: Project?, isDebuggerAgentAvailable: Boolean, isSuccess: Boolean) {
    val status = if (isDebuggerAgentAvailable) {
      if (AsyncStacksUtils.isSuspendHelperEnabled())
        if (isSuccess) DEBUGGER_AGENT_HELPER_THREAD_ENABLED_SUCCESS else DEBUGGER_AGENT_HELPER_THREAD_ENABLED_FAILURE
      else
        if (isSuccess) DEBUGGER_AGENT_HELPER_THREAD_DISABLED_SUCCESS else DEBUGGER_AGENT_HELPER_THREAD_DISABLED_FAILURE
    }
    else {
      if (isSuccess) NO_DEBUGGER_AGENT_SUCCESS else NO_DEBUGGER_AGENT_FAILURE
    }
    evaluationOnPause.log(project, status)
  }

  @JvmStatic
  fun logCoroutineDump(project: Project, coroutinesCount: Int) {
    threadDump.log(project, ThreadDumpStatus.EXTENDED_DUMP, coroutinesCount, -1)
  }

  @JvmStatic
  fun logVirtualThreadsDump(project: Project, virtualThreadsCount: Int) {
    threadDump.log(project, ThreadDumpStatus.EXTENDED_DUMP, -1, virtualThreadsCount)
  }

  @JvmStatic
  fun logPlatformThreadDumpFallback(project: Project, status: ThreadDumpStatus) {
    threadDump.log(project, status, -1, -1)
  }


  private val Breakpoint<*>.type: String? get() = xBreakpoint?.type?.id
}
