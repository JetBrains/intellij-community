// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.diagnostic.freezeAnalyzer

import org.jetbrains.diogen.analysis.freeze.FreezeCauseResult
import org.jetbrains.diogen.analysis.freeze.ParsedThreadDump
import org.jetbrains.diogen.analysis.freeze.ParsedThreadDump.Trace
import org.jetbrains.diogen.analysis.freeze.FreezeAnalyzer as DiogenFreezeAnalyzer

/**
 * Adapts diogen freeze analysis results to platform freeze analysis results.
 */
internal object DiogenFreezeAnalysisAdapter {
  fun analyze(
    threadDumpParsed: ParsedThreadDump,
    testName: String?,
  ): FreezeAnalysisResult? {
    val diogenResult = runCatching {
      DiogenFreezeAnalyzer.analyzeFreeze(threadDumpParsed)
    }.getOrNull() ?: return null
    val threadDump = DiogenThreadDumpViewBuilder.toThreadDumpView(threadDumpParsed)
    val edt = threadDump.findThread(diogenResult.edt) ?: threadDump.threads.firstOrNull { it.isEdt } ?: return null
    return adaptDiogenAnalysis(diogenResult, edt, threadDump, testName)
  }

  private fun adaptDiogenAnalysis(
    diogenResult: FreezeCauseResult,
    edt: DiogenThreadInfo,
    threadDump: DiogenThreadDumpView,
    testName: String?,
  ): FreezeAnalysisResult? {
    with (PlatformFreezeAnalysis) {
      val platformHint = classify(diogenResult, edt, threadDump)
      val edtWaiting = isEdtWaiting(edt)
      return when {
        !edtWaiting && !edt.isSleeping -> adaptBusyEdt(diogenResult, edt, threadDump)
        edtWaiting && isWriteLockWait(edt) ->
          adaptReadWriteLockFreeze(diogenResult, platformHint, edt, threadDump)
        edtWaiting && !isEDTFreezed(edt) && testName == null -> null
        edtWaiting && !isEDTFreezed(edt) && testName != null ->
          freezeAnalysisResult("${testName}: EDT is not blocked/busy (freeze can be the result of extensive GC)", listOf(edt))
        edtWaiting -> adaptGeneralLockFreeze(platformHint, edt)
        else -> null
      }
    }
  }

  private fun adaptBusyEdt(
    diogenResult: FreezeCauseResult,
    edt: DiogenThreadInfo,
    threadDump: DiogenThreadDumpView,
  ): FreezeAnalysisResult? {
    val callable = PlatformFreezeAnalysis.findFirstRelevantMethod(edt.stackTrace)
                   ?: selectedCallable(diogenResult.cause, threadDump)
    return callable?.let { freezeAnalysisResult("EDT is busy with $it", listOf(edt)) }
  }

  private fun adaptReadWriteLockFreeze(
    diogenResult: FreezeCauseResult,
    platformHint: PlatformFreezeHint?,
    edt: DiogenThreadInfo,
    threadDump: DiogenThreadDumpView,
  ): FreezeAnalysisResult? {
    val hint = platformHint ?: return null
    val cause = hint.owner ?: return null
    val causeMethod = hint.ownerMethod
                      ?: selectedCallable(diogenResult.cause, threadDump)
                      ?: PlatformFreezeAnalysis.findFirstRelevantMethod(cause.stackTrace)
                      ?: return null

    return when (hint.kind) {
      PlatformFreezeHint.Kind.LONG_READ_ACTION ->
        freezeAnalysisResult("Long read action in $causeMethod", listOf(cause, edt))

      PlatformFreezeHint.Kind.READ_LOCK_OWNER_BLOCKED,
      PlatformFreezeHint.Kind.READ_LOCK_OWNER_BLOCKED_BY_RW_LOCK_WAITER -> {
        val blocked = hint.blocked ?: return null
        val blockedMethod = PlatformFreezeAnalysis.findFirstRelevantMethod(blocked.stackTrace) ?: return null
        if (hint.kind == PlatformFreezeHint.Kind.READ_LOCK_OWNER_BLOCKED_BY_RW_LOCK_WAITER) {
          freezeAnalysisResult(
            "Possible deadlock. Read lock is taken by $causeMethod, but the thread is blocked by $blockedMethod which is waiting on RWLock",
            listOf(cause, blocked, edt),
            additionalMessage = "${cause.name} took RWLock but it's blocked by ${blocked.name} which waits on RWLock",
          )
        }
        else {
          freezeAnalysisResult(
            "Read lock is taken by $causeMethod, but this thread is blocked by $blockedMethod",
            listOf(cause, blocked, edt),
            additionalMessage = "${cause.name} took RWLock but it's blocked by ${blocked.name}",
          )
        }
      }

      else -> null
    }
  }

  private fun adaptGeneralLockFreeze(platformHint: PlatformFreezeHint?, edt: DiogenThreadInfo): FreezeAnalysisResult? {
    val relevantMethodFromEdt = PlatformFreezeAnalysis.findFirstRelevantMethod(edt.stackTrace) ?: return null
    val hint = platformHint ?: return null
    return when (hint.kind) {
      PlatformFreezeHint.Kind.EDT_RUN_BLOCKING ->
        freezeAnalysisResult("EDT is blocked on $relevantMethodFromEdt which called runBlocking", listOf(edt))

      PlatformFreezeHint.Kind.GENERAL_LOCK_HOLDER -> {
        val lockHolder = hint.owner ?: return null
        val methodFromLockHolder = PlatformFreezeAnalysis.findFirstRelevantMethod(lockHolder.stackTrace) ?: return null
        freezeAnalysisResult(
          "EDT is blocked on $relevantMethodFromEdt",
          listOf(edt, lockHolder),
          additionalMessage = "Possibly locked by $methodFromLockHolder in ${lockHolder.name}",
        )
      }

      else -> null
    }
  }

  private fun selectedCallable(trace: Trace?, threadDump: DiogenThreadDumpView): String? {
    if (trace == null) return null
    return threadDump.findThread(trace)?.stackTrace?.let { stackTrace: String ->
      PlatformFreezeAnalysis.findFirstRelevantMethod(stackTrace)
    } ?: DiogenFreezeAnalyzer.selectCallable(trace)
  }

  private fun freezeAnalysisResult(
    message: String,
    threads: List<DiogenThreadInfo>,
    additionalMessage: String? = null,
  ): FreezeAnalysisResult =
    FreezeAnalysisResult(message, threads.map { FreezeAnalysisThread(it.stackTrace) }, additionalMessage)
}
