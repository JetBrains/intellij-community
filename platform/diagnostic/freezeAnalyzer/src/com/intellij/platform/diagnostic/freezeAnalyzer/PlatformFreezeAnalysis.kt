// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.diagnostic.freezeAnalyzer

import org.jetbrains.diogen.analysis.freeze.FreezeCauseResult

/**
 * Provides platform-specific freeze classification used to preserve legacy messages.
 */
internal object PlatformFreezeAnalysis {
  fun classify(
    diogenResult: FreezeCauseResult,
    edt: DiogenThreadInfo,
    threadDump: DiogenThreadDumpView,
  ): PlatformFreezeHint? =
    when {
      !isEdtWaiting(edt) -> null
      isWriteLockWait(edt) ->
        classifyReadWriteLockFreezeFromDiogen(diogenResult, threadDump)
        ?: classifyReadWriteLockFreezeForLegacyMessage(threadDump)
      isRunBlockingFreeze(edt) -> PlatformFreezeHint(PlatformFreezeHint.Kind.EDT_RUN_BLOCKING)
      else ->
        classifyGeneralLockFreezeFromDiogen(diogenResult, threadDump)
        ?: classifyGeneralLockFreezeForLegacyMessage(edt, threadDump)
    }

  fun isEdtWaiting(threadState: DiogenThreadInfo): Boolean =
    threadState.isWaiting || isWaitingOnSuvorov(threadState)

  fun isEDTFreezed(edt: DiogenThreadInfo): Boolean =
    findFirstRelevantMethod(edt.stackTrace) != "com.intellij.ide.IdeEventQueue.getNextEvent"

  fun isWriteLockWait(threadState: DiogenThreadInfo): Boolean =
    threadState.stackTrace.lineSequence().map { it.trimStart() }.any {
      it.startsWith("at com.intellij.openapi.application.impl.ReadMostlyRWLock.writeLock") ||
      it.startsWith("at com.intellij.openapi.application.impl.AnyThreadWriteThreadingSupport.getWritePermit") ||
      it.startsWith("at com.intellij.openapi.application.impl.ComputationState.upgradeWritePermit") ||
      it.contains("SuvorovProgress")
    }

  fun findFirstRelevantMethod(stackTrace: String): String? {
    val methodList = getMethodList(stackTrace).toList()
    if (methodList.isEmpty()) return null

    val readActions = listOf(
      "at com.intellij.openapi.application.impl.ApplicationImpl.runReadAction",
      "at com.intellij.openapi.application.ActionsKt.runReadAction",
      "at com.intellij.openapi.application.ReadAction.compute",
      "at com.intellij.openapi.application.ReadAction.run",
      "at com.intellij.openapi.application.rw.InternalReadAction.insideReadAction"
    )

    var blockingMethod: String? = null
    methodList.forEachIndexed { i, line ->
      if (readActions.any { line.startsWith(it) }) {
        for (j in i - 1 downTo 0) {
          val candidate = methodList[j]
          if (candidate.isRelevantMethod()) {
            blockingMethod = candidate
            break
          }
        }
        if (blockingMethod != null) return@forEachIndexed
      }
    }
    if (blockingMethod != null) return extractMethodName(blockingMethod)

    return (methodList.firstOrNull { it.isRelevantMethod() } ?: methodList.firstOrNull { !it.isJDKMethod() })?.let { extractMethodName(it) }
  }

  private fun classifyReadWriteLockFreezeFromDiogen(
    diogenResult: FreezeCauseResult,
    threadDump: DiogenThreadDumpView,
  ): PlatformFreezeHint? {
    val cause = threadDump.findThread(diogenResult.cause)
                ?: diogenResult.raThreads.firstNotNullOfOrNull { threadDump.findThread(it) }
                ?: return null
    if (cause.isEdt || cause.name == "Coroutine dump" || cause.isKnownJdkThread) return null
    if (isWaitingOnReadWriteLock(cause) || !isReadWriteLockTaken(cause.stackTrace)) return null

    val blocked = diogenResult.blockedThreads.asSequence()
                    .mapNotNull { threadDump.findThread(it) }
                    .firstOrNull { isWaitingOnReadWriteLock(it) || it.isAwaitedBy(cause) }
                  ?: threadDump.threads.firstOrNull { it.isAwaitedBy(cause) }

    return readWriteLockHint(owner = cause, blocked = blocked)
  }

  private fun classifyReadWriteLockFreezeForLegacyMessage(threadDump: DiogenThreadDumpView): PlatformFreezeHint? {
    val owner = threadDump.threads.asSequence()
                  .filter { it.name != "Coroutine dump" }
                  .filter { !isWaitingOnReadWriteLock(it) && !it.isKnownJdkThread }
                  .filter { !it.isEdt }
                  .firstOrNull { isReadWriteLockTaken(it.stackTrace) }
                ?: return null

    return readWriteLockHint(
      owner = owner,
      blocked = threadDump.threads.firstOrNull { it.isAwaitedBy(owner) },
      ownerMethod = findFirstRelevantMethod(owner.stackTrace),
    )
  }

  private fun readWriteLockHint(owner: DiogenThreadInfo, blocked: DiogenThreadInfo?, ownerMethod: String? = null): PlatformFreezeHint =
    when {
      blocked == null ->
        PlatformFreezeHint(PlatformFreezeHint.Kind.LONG_READ_ACTION, owner = owner, ownerMethod = ownerMethod)

      isWaitingOnReadWriteLock(blocked) ->
        PlatformFreezeHint(
          PlatformFreezeHint.Kind.READ_LOCK_OWNER_BLOCKED_BY_RW_LOCK_WAITER,
          owner = owner,
          blocked = blocked,
          ownerMethod = ownerMethod,
        )

      else ->
        PlatformFreezeHint(
          PlatformFreezeHint.Kind.READ_LOCK_OWNER_BLOCKED,
          owner = owner,
          blocked = blocked,
          ownerMethod = ownerMethod,
        )
    }

  private fun classifyGeneralLockFreezeFromDiogen(diogenResult: FreezeCauseResult, threadDump: DiogenThreadDumpView): PlatformFreezeHint? {
    val lockHolder = threadDump.findThread(diogenResult.cause)?.takeIf { !it.isEdt && !it.isWaiting }
                     ?: diogenResult.blockedThreads.asSequence()
                       .mapNotNull { threadDump.findThread(it) }
                       .firstOrNull { !it.isEdt && !it.isWaiting }
                     ?: return null
    return PlatformFreezeHint(PlatformFreezeHint.Kind.GENERAL_LOCK_HOLDER, owner = lockHolder)
  }

  private fun classifyGeneralLockFreezeForLegacyMessage(
    edt: DiogenThreadInfo,
    threadDump: DiogenThreadDumpView,
  ): PlatformFreezeHint? {
    for (method in getPotentialLockMethods(edt.stackTrace)) {
      val className = method.extractClassFromMethod()
      val lockHolder = threadDump.threads.asSequence()
        .filter { it.name != "Coroutine dump" }
        .filter { !it.isWaiting }
        .firstOrNull { it.stackTrace.contains(className) }
      if (lockHolder != null) return PlatformFreezeHint(PlatformFreezeHint.Kind.GENERAL_LOCK_HOLDER, owner = lockHolder)
    }
    return null
  }

  private fun isWaitingOnSuvorov(edt: DiogenThreadInfo): Boolean =
    getMethodList(edt.stackTrace).any { it.contains("SuvorovProgress") }

  private fun isWaitingOnReadWriteLock(threadState: DiogenThreadInfo): Boolean =
    threadState.isWaiting && threadState.stackTrace.lineSequence()
      .drop(2)
      .map { it.trimStart() }
      .any {
        it.startsWith("at com.intellij.openapi.application.impl.ReadMostlyRWLock.waitABit") ||
        it.startsWith("at com.intellij.openapi.application.impl.AnyThreadWriteThreadingSupport.getReadPermit") ||
        it.startsWith("at com.intellij.openapi.application.impl.AnyThreadWriteThreadingSupport.getWritePermit") ||
        it.startsWith("at com.intellij.openapi.application.impl.ComputationState.acquireReadPermit") ||
        it.startsWith("at com.intellij.openapi.application.impl.ComputationState.upgradeWritePermit") ||
        it.startsWith("at com.intellij.platform.locking.impl.RunSuspend.await")
      }

  private fun isReadWriteLockTaken(stackTrace: String): Boolean =
    stackTrace.lineSequence()
      .map { it.trim() }
      .firstOrNull { it.isLockMethod() } != null

  private fun getMethodList(stackTrace: String) = stackTrace.lineSequence()
    .map { it.trim() }
    .filter { it.startsWith("at") }

  private fun getPotentialLockMethods(stackTrace: String): Sequence<String> =
    getMethodList(stackTrace)
      .filter { !it.isJDKMethod() }
      .filter { !it.startsWith("at com.intellij.util.concurrency") }
      .filter { !it.startsWith("at com.intellij.openapi.progress.util.ProgressIndicatorUtils") }

  private fun isRunBlockingFreeze(edt: DiogenThreadInfo): Boolean =
    edt.stackTrace.contains("on kotlinx.coroutines.BlockingCoroutine") &&
    getMethodList(edt.stackTrace).any { it.contains("BlockingCoroutine.joinBlocking") }

  private fun extractMethodName(line: String): String {
    val startIndex = line.indexOf("at ") + 3
    val endIndex = line.indexOf('(')
    return if (startIndex in 0 until endIndex) line.substring(startIndex, endIndex) else ""
  }

  private fun String.isLockMethod(): Boolean =
    startsWith("at com.intellij.openapi.application.impl.RwLockHolder.tryRunReadAction") ||
    startsWith("at com.intellij.openapi.application.ReadAction.compute") ||
    startsWith("at com.intellij.openapi.application.impl.AnyThreadWriteThreadingSupport.runWriteAction") ||
    startsWith("at com.intellij.openapi.application.impl.AnyThreadWriteThreadingSupport.runReadAction") ||
    startsWith("at com.intellij.openapi.application.impl.AnyThreadWriteThreadingSupport.runWriteIntentReadAction") ||
    startsWith("at com.intellij.openapi.application.impl.AnyThreadWriteThreadingSupport.tryRunReadAction") ||
    contains("NestedLocksThreadingSupport.runWriteAction") ||
    contains("NestedLocksThreadingSupport.runReadAction") ||
    contains("NestedLocksThreadingSupport.runWriteIntentReadAction") ||
    contains("NestedLocksThreadingSupport.tryRunReadAction")

  private fun String.isJDKMethod(): Boolean {
    val jdkList = listOf(
      "at java.",
      "at jdk.",
      "at kotlin.",
      "at kotlinx.",
    )
    return jdkList.any { this.startsWith(it) }
  }

  private fun String.isRelevantMethod(): Boolean {
    val irrelevantStarts = listOf(
      "at com.intellij.openapi.diagnostic.",
      "at com.intellij.idea.IdeaLogger.warn",
      "at com.intellij.util.",
      "at com.intellij.ide.",
      "at com.intellij.serialization.",
      "at com.intellij.openapi.progress.util.",
      "at com.intellij.openapi.vfs.",
      "at com.intellij.openapi.util.",
      "at it.unimi.dsi.fastutil.",
      "at com.google.common.collect.",
      "at com.intellij.psi.",
      "at com.intellij.indexing.composite.",
      "at com.intellij.openapi.progress.",
      "at com.intellij.openapi.application.",
      "at platform/jdk.zipfs",
      "at net.jpountz.lz4.",
      "at com.intellij.concurrency.",
      "at com.intellij.platform.locking."
    )
    if (this.contains("\$\$Lambda")) return false
    return !isJDKMethod() && irrelevantStarts.none { this.startsWith(it) }
  }

  private fun String.extractClassFromMethod(): String =
    split('(').first().split('.').dropLast(1).last()
}

internal data class PlatformFreezeHint(
  val kind: Kind,
  val owner: DiogenThreadInfo? = null,
  val blocked: DiogenThreadInfo? = null,
  val ownerMethod: String? = null,
) {
  enum class Kind {
    LONG_READ_ACTION,
    READ_LOCK_OWNER_BLOCKED,
    READ_LOCK_OWNER_BLOCKED_BY_RW_LOCK_WAITER,
    EDT_RUN_BLOCKING,
    GENERAL_LOCK_HOLDER,
  }
}
