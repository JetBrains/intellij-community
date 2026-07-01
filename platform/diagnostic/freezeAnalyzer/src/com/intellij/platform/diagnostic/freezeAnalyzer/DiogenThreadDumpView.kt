// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.diagnostic.freezeAnalyzer

import org.jetbrains.diogen.analysis.freeze.ParsedThreadDump
import org.jetbrains.diogen.analysis.freeze.ParsedThreadDump.Line
import org.jetbrains.diogen.analysis.freeze.ParsedThreadDump.Trace

private val THREAD_START_PATTERN = Regex("^\"(.+)\".+((?:prio=\\d+ )?(?:os_prio=\\S+ )?.*tid=\\S+(?: nid=\\S+)?|[Ii][Dd]=\\d+) ([^\\[]+)")
private val FORCED_THREAD_START_PATTERN = Regex("^Thread (\\d+): \\(state = (.+)\\)")
private val JCMD_THREAD_START_PATTERN = Regex("^#\\d+ \"(.*)\"(.*)")
private val YOURKIT_THREAD_START_PATTERN = Regex("(.+) \\[([A-Z_, ]*)]")
private val YOURKIT_THREAD_START_PATTERN_2 = Regex("(.+) (?:State:)? (.+) CPU usage on sample: .+")
private val THREAD_STATE_PATTERN = Regex("java\\.lang\\.Thread\\.State: (.+) \\((.+)\\)")
private val THREAD_STATE_PATTERN_2 = Regex("java\\.lang\\.Thread\\.State: (.+)")
private val WAITING_FOR_LOCK_PATTERN = Regex("- waiting (?:on|to lock) <(.+)>")
private val PARKING_TO_WAIT_FOR_LOCK_PATTERN = Regex("- parking to wait for {2}<(.+)>")
private val WAITING_FOR_RELEASE_PATTERN = Regex("waiting for .+ to release lock on (?:<(.+?)>|(\\S+))")
private val LOCKED_PATTERN = Regex("- locked (?:<(.+?)>|(\\S+))")
private val LOCKED_OWNABLE_SYNCHRONIZERS_PATTERN = Regex("- <(0x[\\da-f]+)> \\(.*\\)")
private val IDLE_TIMER_THREAD_PATTERN = Regex("java\\.lang\\.Object\\.wait\\([^()]+\\)\\s+at java\\.util\\.TimerThread\\.mainLoop")
private val IDLE_SWING_TIMER_THREAD_PATTERN = Regex("java\\.lang\\.Object\\.wait\\([^()]+\\)\\s+at javax\\.swing\\.TimerQueue\\.run")

private const val LOCKED_OWNABLE_SYNCHRONIZERS_HEADER = "Locked ownable synchronizers"
private const val JAVA_LANG_OBJECT_WAIT = "java.lang.Object.wait("

/**
 * Builds the platform-specific view over Diogen parsed thread dumps used by freeze analyzer heuristics.
 */
internal object DiogenThreadDumpViewBuilder {
  fun toThreadDumpView(threadDump: ParsedThreadDump): DiogenThreadDumpView {
    val threads = threadDump.traces.mapNotNull { it.toThreadInfo() }.toMutableList()
    linkDiogenAwaitedThreads(threads)
    threads.sortWith { first, second -> getInterestLevel(second) - getInterestLevel(first) }
    return DiogenThreadDumpView(threads)
  }
}

internal class DiogenThreadDumpView(internal val threads: List<DiogenThreadInfo>) {
  fun findThread(trace: Trace?): DiogenThreadInfo? {
    if (trace == null) return null
    val platformThreadHeader = trace.findPlatformThreadHeader()
    val threadName = platformThreadHeader?.header?.name ?: extractThreadName(trace.name)
    val traceHeader = platformThreadHeader?.line?.toString()?.trim() ?: trace.name.toString().trim()
    return threads.firstOrNull { it.trace == trace }
           ?: threads.firstOrNull { it.name == threadName }
           ?: threads.firstOrNull { it.stackTrace.lineSequence().firstOrNull()?.trim() == traceHeader }
  }
}

internal class DiogenThreadInfo(
  val trace: Trace,
  val name: String,
  val state: String,
  val stackTrace: String,
  val isEmptyStackTrace: Boolean,
  val threadStateDetail: String?,
  val contendedMonitor: String?,
  val ownedMonitors: Set<String>,
  val ownableSynchronizers: String?,
) {
  private val awaitingThreads = HashSet<DiogenThreadInfo>()

  val isEdt: Boolean
    get() = isEdtThreadName(name)

  val isWaiting: Boolean
    get() = threadStateDetail == "on object monitor" ||
            state.startsWith("waiting") ||
            (threadStateDetail == "parking" && !isSleeping)

  val isSleeping: Boolean
    get() = threadStateDetail == "sleeping" ||
            ((threadStateDetail == "parking" || state == "waiting on condition") && isThreadPoolExecutor())

  val isKnownJdkThread: Boolean
    get() = isKnownJdkThread(stackTrace)

  fun isAwaitedBy(thread: DiogenThreadInfo): Boolean =
    awaitingThreads.contains(thread)

  internal fun addWaitingThread(thread: DiogenThreadInfo) {
    awaitingThreads.add(thread)
  }

  private fun isThreadPoolExecutor(): Boolean =
    stackTrace.contains("java.util.concurrent.ScheduledThreadPoolExecutor\$DelayedWorkQueue.take") ||
    stackTrace.contains("java.util.concurrent.ThreadPoolExecutor.getTask")
}

private fun Trace.toThreadInfo(): DiogenThreadInfo? {
  val (headerLine, threadHeader) = findPlatformThreadHeader() ?: return null
  val stackTrace = textFrom(headerLine)
  val javaThreadState = findJavaThreadState(stackTrace)
  val threadStateDetail = findThreadStateDetail(threadHeader.name, stackTrace, javaThreadState)
  val ownedMonitors = LinkedHashSet<String>()
  LOCKED_PATTERN.findAll(stackTrace).forEach {
    it.firstNonEmptyGroup()?.let { monitor -> ownedMonitors.add(monitor) }
  }
  val ownableSynchronizers = findLockedOwnableSynchronizers(stackTrace)
  if (ownableSynchronizers != null) {
    ownedMonitors.add(ownableSynchronizers)
  }
  return DiogenThreadInfo(
    trace = this,
    name = threadHeader.name,
    state = threadHeader.state,
    stackTrace = stackTrace,
    isEmptyStackTrace = lines.none { it.isAfter(headerLine) && it.trimmedStartsWith("at") },
    threadStateDetail = threadStateDetail,
    contendedMonitor = findWaitingForLock(stackTrace),
    ownedMonitors = ownedMonitors,
    ownableSynchronizers = ownableSynchronizers,
  )
}

private fun Trace.findPlatformThreadHeader(): PlatformThreadHeaderLine? {
  parseThreadHeader(name.toString().trim())?.let {
    return PlatformThreadHeaderLine(name, it)
  }
  for (line in lines) {
    parseThreadHeader(line.toString().trim())?.let {
      return PlatformThreadHeaderLine(line, it)
    }
  }
  return null
}

private fun Trace.textFrom(headerLine: Line): String =
  String(dump, headerLine.start, (lines.lastOrNull() ?: headerLine).end - headerLine.start)

private fun parseThreadHeader(header: String): PlatformThreadHeader? {
  THREAD_START_PATTERN.find(header)?.let {
    return PlatformThreadHeader(it.groupValues[1], normalizeThreadState(it.groupValues[3]))
  }
  FORCED_THREAD_START_PATTERN.matchEntire(header)?.let {
    return PlatformThreadHeader(it.groupValues[1], normalizeThreadState(it.groupValues[2]))
  }
  JCMD_THREAD_START_PATTERN.matchEntire(header)?.let {
    return PlatformThreadHeader(it.groupValues[1], "unknown")
  }
  YOURKIT_THREAD_START_PATTERN.matchEntire(header)?.let {
    return PlatformThreadHeader(it.groupValues[1], normalizeThreadState(it.groupValues[2]))
  }
  YOURKIT_THREAD_START_PATTERN_2.matchEntire(header)?.let {
    return PlatformThreadHeader(it.groupValues[1], normalizeThreadState(it.groupValues[2]))
  }
  return null
}

private fun normalizeThreadState(state: String): String =
  state.split(' ')
    .filter { it.isNotEmpty() && it != "virtual" && it != "unmounted" && !it.contains("=") }
    .joinToString(" ")

private fun findJavaThreadState(stackTrace: String): JavaThreadState? {
  THREAD_STATE_PATTERN.find(stackTrace)?.let {
    return JavaThreadState(it.groupValues[1], it.groupValues[2].trim())
  }
  THREAD_STATE_PATTERN_2.find(stackTrace)?.let {
    return JavaThreadState(it.groupValues[1], null)
  }
  return null
}

private fun findThreadStateDetail(name: String, stackTrace: String, javaThreadState: JavaThreadState?): String? =
  when {
    stackTrace.contains("java.lang.Thread.sleep") && javaThreadState?.state != Thread.State.RUNNABLE.name -> "sleeping"
    isEdtThreadName(name) && stackTrace.contains("java.awt.EventQueue.getNextEvent") -> "idle"
    else -> javaThreadState?.detail
  }

private fun findWaitingForLock(stackTrace: String): String? =
  WAITING_FOR_LOCK_PATTERN.find(stackTrace)?.groupValues?.get(1)
  ?: PARKING_TO_WAIT_FOR_LOCK_PATTERN.find(stackTrace)?.groupValues?.get(1)
  ?: WAITING_FOR_RELEASE_PATTERN.find(stackTrace)?.firstNonEmptyGroup()

private fun findLockedOwnableSynchronizers(stackTrace: String): String? {
  if (!stackTrace.contains(LOCKED_OWNABLE_SYNCHRONIZERS_HEADER)) return null
  return LOCKED_OWNABLE_SYNCHRONIZERS_PATTERN.find(stackTrace)?.groupValues?.get(1)
}

private fun linkDiogenAwaitedThreads(threadInfos: List<DiogenThreadInfo>) {
  val monitorToOwners = HashMap<String, MutableList<DiogenThreadInfo>>()
  for (threadInfo in threadInfos) {
    for (monitor in threadInfo.ownedMonitors) {
      monitorToOwners.getOrPut(monitor) { mutableListOf() }.add(threadInfo)
    }
  }

  for (threadInfo in threadInfos) {
    val waitedMonitor = threadInfo.contendedMonitor ?: continue
    val lockOwner = findDiogenLockOwner(monitorToOwners[waitedMonitor], threadInfos, waitedMonitor, ignoreWaiting = true)
                    ?: findDiogenLockOwner(monitorToOwners[waitedMonitor], threadInfos, waitedMonitor, ignoreWaiting = false)
                    ?: continue
    lockOwner.addWaitingThread(threadInfo)
  }
}

private fun findDiogenLockOwner(
  lockOwners: List<DiogenThreadInfo>?,
  threadInfos: List<DiogenThreadInfo>,
  lockId: String,
  ignoreWaiting: Boolean,
): DiogenThreadInfo? {
  lockOwners?.firstOrNull { !ignoreWaiting || !it.stackTrace.contains(JAVA_LANG_OBJECT_WAIT) }?.let { return it }
  return threadInfos.firstOrNull { it.ownableSynchronizers == lockId }
}

private fun getInterestLevel(threadInfo: DiogenThreadInfo): Int =
  when {
    threadInfo.isEmptyStackTrace -> -10
    threadInfo.isKnownJdkThread -> -5
    threadInfo.isSleeping -> -2
    else -> threadInfo.stackTrace.count { it == '\n' }
  }

private fun extractThreadName(header: CharSequence): String {
  val start = header.indexOf('"')
  if (start < 0) return header.toString()
  val end = header.indexOf('"', start + 1)
  return if (end < 0) header.toString() else header.subSequence(start + 1, end).toString()
}

private fun isKnownJdkThread(stackTrace: String): Boolean =
  stackTrace.contains("java.lang.ref.Reference\$ReferenceHandler.run") ||
  stackTrace.contains("java.lang.ref.Finalizer\$FinalizerThread.run") ||
  stackTrace.contains("sun.awt.AWTAutoShutdown.run") ||
  stackTrace.contains("sun.java2d.Disposer.run") ||
  stackTrace.contains("sun.awt.windows.WToolkit.eventLoop") ||
  stackTrace.contains("jdk.internal.ref.CleanerImpl.run") ||
  IDLE_TIMER_THREAD_PATTERN.containsMatchIn(stackTrace) ||
  IDLE_SWING_TIMER_THREAD_PATTERN.containsMatchIn(stackTrace)

private fun isEdtThreadName(name: CharSequence): Boolean = when {
  java.lang.Boolean.getBoolean("jb.dispatching.on.main.thread") -> name.contains("AppKit")
  else -> name.contains("AWT-EventQueue")
}

private fun MatchResult.firstNonEmptyGroup(): String? =
  groupValues.asSequence().drop(1).firstOrNull { it.isNotEmpty() }

private fun CharSequence.trimmedStartsWith(prefix: String): Boolean {
  val start = indexOfFirst { !it.isWhitespace() }
  if (start < 0 || length - start < prefix.length) return false
  for (i in prefix.indices) {
    if (this[start + i] != prefix[i]) return false
  }
  return true
}

private fun Line.isAfter(other: Line): Boolean =
  start > other.start

private data class JavaThreadState(val state: String, val detail: String?)

private data class PlatformThreadHeader(val name: String, val state: String)

private data class PlatformThreadHeaderLine(val line: Line, val header: PlatformThreadHeader)
