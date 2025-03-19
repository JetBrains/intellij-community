package com.intellij.platform.diagnostic.freezeAnalyzer

import com.intellij.threadDumpParser.ThreadDumpParser
import com.intellij.threadDumpParser.ThreadState
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object FreezeAnalyzer {

  /**
   * Analyze freeze based on the IJ Platform knowledge and try to infer the relevant message.
   * If analysis fails, it returns `null`.
   */
  fun analyzeFreeze(threadDump: String, testName: String? = null): FreezeAnalysisResult? {
    val threadDumpWithoutCoroutine = threadDump.split("---------- Coroutine dump ----------")[0]
    val threadDumpParsed = ThreadDumpParser.parse(threadDumpWithoutCoroutine)
    val edtThread = threadDumpParsed.firstOrNull { it.isEDT }
    return edtThread?.let { analyzeEDThread(it, threadDumpParsed, testName) }
  }

  private fun analyzeEDThread(edt: ThreadState, threadDumpParsed: List<ThreadState>, testName: String?): FreezeAnalysisResult? =
    when {
      !edt.isWaiting && !edt.isSleeping -> findFirstRelevantMethod(edt.stackTrace)?.let { FreezeAnalysisResult("EDT is busy with $it", listOf(edt)) }
      edt.isWaiting && isWriteLockWait(edt) -> findThreadThatTookReadWriteLock(threadDumpParsed)?.let { FreezeAnalysisResult(it.message, it.threads + listOf(edt), it.additionalMessage) }
      edt.isWaiting && !isEDTFreezed(edt) && testName == null -> null
      edt.isWaiting && !isEDTFreezed(edt) && testName != null -> FreezeAnalysisResult("${testName}: EDT is not blocked/busy (freeze can be the result of extensive GC)", listOf(edt))
      edt.isWaiting -> analyzeLock(edt, threadDumpParsed)
      else -> null
    }

  private fun isEDTFreezed(edt: ThreadState): Boolean {
    return findFirstRelevantMethod(edt.stackTrace) != "com.intellij.ide.IdeEventQueue.getNextEvent"
  }

  private fun analyzeLock(edt: ThreadState, threadDumpParsed: List<ThreadState>): FreezeAnalysisResult? {
    val relevantMethodFromEdt = findFirstRelevantMethod(edt.stackTrace)
    if (relevantMethodFromEdt == null) return null
    var possibleThreadWithLock: ThreadState? = null
    for (it in getPotentialMethodsWithLock(edt.stackTrace)) {
      val clazz = extractClassFromMethod(it)
      possibleThreadWithLock = threadDumpParsed.asSequence()
        .filter { !it.isWaiting }
        .firstOrNull { it.stackTrace.contains(clazz) }
      if (possibleThreadWithLock != null) {
        break
      }
    }
    if (possibleThreadWithLock == null) return null
    val methodFromThreadWithLock = findFirstRelevantMethod(possibleThreadWithLock.stackTrace)
    if (methodFromThreadWithLock != null) {
      return FreezeAnalysisResult("EDT is blocked on $relevantMethodFromEdt", listOf(edt, possibleThreadWithLock), additionalMessage = "Possibly locked by $methodFromThreadWithLock in ${possibleThreadWithLock.name}")
    }
    return null
  }

  private fun getPotentialMethodsWithLock(stackTrace: String): List<String> {
    return getMethodList(stackTrace)
      .filter { !it.isJDKMethod() }
      .filter { !it.startsWith("at com.intellij.util.concurrency") }
      .filter { !it.startsWith("at com.intellij.openapi.progress.util.ProgressIndicatorUtils") }
      .toList()

  }

  private fun isWriteLockWait(threadState: ThreadState): Boolean =
    threadState.stackTrace.lineSequence().map { it.trimStart() }.any {
      it.startsWith("at com.intellij.openapi.application.impl.ReadMostlyRWLock.writeLock") ||
      it.startsWith("at com.intellij.openapi.application.impl.AnyThreadWriteThreadingSupport.getWritePermit")
    }

  private fun findThreadThatTookReadWriteLock(threadDumpParsed: List<ThreadState>): FreezeAnalysisResult? =
    threadDumpParsed.asSequence()
      .filter { !isWaitingOnReadWriteLock(it) && !it.isKnownJDKThread }
      .firstOrNull { isReadWriteLockTaken(it.stackTrace) }
      ?.let { threadState ->
        threadDumpParsed.firstOrNull { it.isAwaitedBy(threadState) }?.let {
          if (isWaitingOnReadWriteLock(it)) {
            FreezeAnalysisResult("Possible deadlock. Read lock is taken by ${findFirstRelevantMethod(threadState.stackTrace)}, but the thread is blocked by ${findFirstRelevantMethod(it.stackTrace)} which is waiting on RWLock",
                                 listOf(threadState, it), additionalMessage = "${threadState.name} took RWLock but it's blocked by ${it.name} which waits on RWLock")
          }
          else {
            FreezeAnalysisResult("Read lock is taken by ${findFirstRelevantMethod(threadState.stackTrace)}, but this thread is blocked by ${findFirstRelevantMethod(it.stackTrace)}",
                                 listOf(threadState, it), additionalMessage = "${threadState.name} took RWLock but it's blocked by ${it.name}")
          }
        } ?: FreezeAnalysisResult("Long read action in ${findFirstRelevantMethod(threadState.stackTrace)}", listOf(threadState))
      }

  private fun isWaitingOnReadWriteLock(threadState: ThreadState): Boolean =
    threadState.isWaiting && threadState.stackTrace.lineSequence()
      .drop(2)
      .map { it.trimStart() }
      .any {
        it.startsWith("at com.intellij.openapi.application.impl.ReadMostlyRWLock.waitABit") ||
        it.startsWith("at com.intellij.openapi.application.impl.AnyThreadWriteThreadingSupport.getReadPermit") ||
        it.startsWith("at com.intellij.openapi.application.impl.AnyThreadWriteThreadingSupport.getWritePermit")
      }

  private fun isReadWriteLockTaken(stackTrace: String): Boolean =
    stackTrace.lineSequence()
      .map { it.trim() }
      .firstOrNull { it.isLockMethod() } != null

  private fun String.isLockMethod(): Boolean =
    startsWith("at com.intellij.openapi.application.impl.RwLockHolder.tryRunReadAction") ||
    startsWith("at com.intellij.openapi.application.ReadAction.compute") ||
    startsWith("at com.intellij.openapi.application.impl.AnyThreadWriteThreadingSupport.runWriteAction") ||
    startsWith("at com.intellij.openapi.application.impl.AnyThreadWriteThreadingSupport.runReadAction") ||
    startsWith("at com.intellij.openapi.application.impl.AnyThreadWriteThreadingSupport.runWriteIntentReadAction") ||
    startsWith("at com.intellij.openapi.application.impl.AnyThreadWriteThreadingSupport.tryRunReadAction")


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
      "at net.jpountz.lz4."
    )
    return !isJDKMethod() && irrelevantStarts.none { this.startsWith(it) }
  }

  private fun findFirstRelevantMethod(stackTrace: String): String? {
    val methodList = getMethodList(stackTrace)
    return (methodList.firstOrNull { it.isRelevantMethod() } ?: methodList.firstOrNull { !it.isJDKMethod() })?.let { extractMethodName(it) }

  }

  private fun getMethodList(stackTrace: String) = stackTrace.lineSequence()
    .map { it.trim() }
    .filter { it.startsWith("at") }

  private fun extractMethodName(line: String): String {
    val startIndex = line.indexOf("at ") + 3
    val endIndex = line.indexOf('(')
    return if (startIndex in 0 until endIndex) line.substring(startIndex, endIndex) else ""
  }

  private fun extractClassFromMethod(method: String): String {
    return method.split('(').first().split('.').dropLast(1).last()
  }
}

@ApiStatus.Internal
data class FreezeAnalysisResult(val message: String, val threads: List<ThreadState>, val additionalMessage: String? = null)