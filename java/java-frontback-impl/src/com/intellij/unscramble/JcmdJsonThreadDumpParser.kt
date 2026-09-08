// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.unscramble

import com.intellij.threadDumpParser.ThreadDumpParser
import com.intellij.threadDumpParser.ThreadState
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.annotations.ApiStatus

private val jcmdJson = Json { ignoreUnknownKeys = true }
// "ForkJoinPool-1-worker-12" #61 [38403] daemon prio=5 os_prio=31 cpu=48.41ms elapsed=2.98s tid=0x000000087b0fd000 nid=38403 waiting on condition  [0x00000001709f2000]
private val jcmdPlatformThreadIdRegex = Regex("""^(?:"[^"]*"\s+#|#)(\d+)\b""")
private val jcmdPlatformThreadNativeIdRegex = Regex("""^(?:"[^"]*"\s+#|#)\d+\s+\[(\d+)\](?=\s|$)""")
private val jcmdPlatformThreadAttributeRegex = Regex("""\b(prio|os_prio|cpu|elapsed|tid|nid)=([^\s]+)""")
private val jcmdPlatformThreadStackPointerRegex = Regex("""(\[0x[\da-fA-F]+\])\s*$""")
private const val UNSAFE_PARK_FRAME = "jdk.internal.misc.Unsafe.park("

/**
 * Parses the output of `jcmd <pid> Thread.dump_to_file -format=json` preserving the hierarchy of thread containers.
 *
 * Returns `null` if the text is not a jcmd JSON thread dump.
 */
@ApiStatus.Internal
fun parseJcmdJsonThreadDump(text: String): ThreadDumpState? =
  parseJcmdJsonThreadDump(text, emptyList())

@ApiStatus.Internal
fun parseJcmdJsonThreadDump(text: String, platformThreadStates: List<ThreadState>): ThreadDumpState? {
  val dump = runCatching { jcmdJson.decodeFromString<JcmdDump>(text) }.getOrNull() ?: return null

  val containers = dump.threadDump.threadContainers

  val containerNameToId = mutableMapOf<String, Long>()
  val threadStates = mutableListOf<ThreadState>()

  for ((ordinal, container) in containers.withIndex()) {
    val isRoot = container.container == "<root>" && container.parent == null && container.owner == null
    // Skip RootContainer
    val containerId = if (isRoot) {
      null
    } else {
      // Use negative IDs for containers to avoid collision with thread tids
      -(ordinal.toLong() + 1)
    }

    if (containerId != null) {
      containerNameToId[container.container] = containerId
    }

    threadStates.addAll(
      container.threads.map { it.toThreadState(containerId) }
    )
  }

  val containerDescriptors = containers.mapNotNull { it.toJavaThreadContainerDesc(containerNameToId) }

  if (threadStates.isEmpty() && containerDescriptors.isEmpty()) return ThreadDumpState.EMPTY

  // TODO: these steps could be extracted to a separate function and reused in ThreadDumpAction
  ThreadDumpParser.enrichStackTraceWithLockInfo(threadStates)
  val enrichedThreadStates = mergePlatformThreadInfo(threadStates, platformThreadStates)

  for (threadState in enrichedThreadStates) {
    ThreadDumpParser.inferThreadStateDetail(threadState)
  }

  ThreadDumpParser.detectWaitingAndDeadlockedThreads(enrichedThreadStates)

  ThreadDumpParser.sortThreads(enrichedThreadStates)

  return ThreadDumpState(enrichedThreadStates, containerDescriptors)
}

private fun mergePlatformThreadInfo(threadStates: MutableList<ThreadState>, platformThreadStates: List<ThreadState>): List<ThreadState> {
  if (platformThreadStates.isEmpty()) return threadStates

  val platformThreadsById = platformThreadStates.associateBy { it.uniqueId }
  val threadsPresentInJcmdDump = mutableSetOf<Long>()

  for (threadState in threadStates) {
    val tid = threadState.uniqueId ?: continue
    if (!threadState.isVirtual) {
      platformThreadsById[tid]?.let {
        threadState.applyPlatformThreadMetadata(it)
        threadsPresentInJcmdDump.add(tid)
      }
    }
  }
  // Add platform threads, which were not present in JCMD dump or have null uniqueId
  // (expected to add HotSpot threads, missing from jcmd json dump)
  threadStates += platformThreadStates.filter { it.uniqueId == null || it.uniqueId !in threadsPresentInJcmdDump }
  return threadStates
}

private fun ThreadState.applyPlatformThreadMetadata(platformThreadState: ThreadState): ThreadState =
  apply {
    val currentStackTrace = stackTrace ?: return@apply
    val platformMetadata = platformThreadState.stackTrace?.lineSequence()?.firstOrNull()?.platformThreadMetadata() ?: return@apply
    isDaemon = platformThreadState.isDaemon
    setStackTrace(currentStackTrace.replaceFirstLine(jcmdThreadHeader(platformMetadata)), isEmptyStackTrace)
  }

private fun ThreadState.jcmdThreadHeader(platformMetadata: PlatformThreadMetadata): String =
  buildString {
    append("\"").append(name).append("\"")
    uniqueId?.let { append(" #").append(it) }
    platformMetadata.nativeThreadId?.let { append(" [").append(it).append("]") }
    if (isDaemon) append(" daemon")
    platformMetadata.priority?.let { append(" prio=").append(it) }
    platformMetadata.osPriority?.let { append(" os_prio=").append(it) }
    platformMetadata.cpu?.let { append(" cpu=").append(it) }
    platformMetadata.elapsed?.let { append(" elapsed=").append(it) }
    val tid = platformMetadata.tid ?: uniqueId?.toString()
    tid?.let { append(" tid=").append(it) }
    (platformMetadata.nativeThreadId ?: platformMetadata.nid)?.let { append(" nid=").append(it) }
    append(" ").append(state)
    platformMetadata.stackPointer?.let { append(" ").append(it) }
  }

private fun String.platformThreadMetadata(): PlatformThreadMetadata {
  val nativeThreadId = jcmdPlatformThreadNativeIdRegex.find(this)?.groupValues?.get(1)
  var priority: String? = null
  var osPriority: String? = null
  var cpu: String? = null
  var elapsed: String? = null
  var tid: String? = null
  var nid: String? = null
  for (match in jcmdPlatformThreadAttributeRegex.findAll(this)) {
    when (match.groupValues[1]) {
      "prio" -> priority = match.groupValues[2]
      "os_prio" -> osPriority = match.groupValues[2]
      "cpu" -> cpu = match.groupValues[2]
      "elapsed" -> elapsed = match.groupValues[2]
      "tid" -> tid = match.groupValues[2]
      "nid" -> nid = match.groupValues[2]
    }
  }
  return PlatformThreadMetadata(
    nativeThreadId = nativeThreadId,
    priority = priority,
    osPriority = osPriority,
    cpu = cpu,
    elapsed = elapsed,
    tid = tid,
    nid = nid,
    stackPointer = jcmdPlatformThreadStackPointerRegex.find(this)?.groupValues?.get(1),
  )
}

private fun String.replaceFirstLine(newFirstLine: String): String {
  val firstLineEnd = indexOf('\n')
  return if (firstLineEnd < 0) newFirstLine else newFirstLine + substring(firstLineEnd)
}

private data class PlatformThreadMetadata(
  val nativeThreadId: String?,
  val priority: String?,
  val osPriority: String?,
  val cpu: String?,
  val elapsed: String?,
  val tid: String?,
  val nid: String?,
  val stackPointer: String?,
)

private fun JcmdThread.toThreadState(containerId: Long?): ThreadState {
  val threadState = ThreadState(name, state)

  val rawStackTrace = stack
    .filter { it.isNotEmpty() }
    .joinToString(separator = "\n") { "\tat $it" }

  threadState.isVirtual = virtual ?: false

  val stackTrace = buildString {
    append("\"$name\" tid=$tid")
    if (threadState.isVirtual) {
      val carrierInfo = if (carrier != null) "carrierId=$carrier" else "unmounted"
      append(" virtual $carrierInfo")
    }
    append(" $state")
    append("\n")
    append(rawStackTrace)
  }

  threadState.setStackTrace(stackTrace, rawStackTrace.isEmpty())

  val tidLong = tid.toLongOrNull()
  if (tidLong != null) {
    threadState.uniqueId = tidLong
  }
  threadState.threadContainerUniqueId = containerId

  threadState.extractJcmdJsonLockInfo(this)

  return threadState
}

private fun JcmdContainer.toJavaThreadContainerDesc(containerNameToId: Map<String, Long>): JavaThreadContainerDesc? {
  val containerId = containerNameToId[container] ?: return null
  val parentId = owner?.toLongOrNull() ?: parent?.let { containerNameToId[parent] }
  return JavaThreadContainerDesc(
    name = container,
    containerId = containerId,
    parentId = parentId,
  )
}

private fun ThreadState.extractJcmdJsonLockInfo(thread: JcmdThread) {
  val parkBlocker = thread.parkBlocker?.objectRef?.takeIf { it.isNotEmpty() }
  contendedMonitor = thread.blockedOn ?: thread.waitingOn ?: parkBlocker
  if (parkBlocker != null) {
    addParkingToWaitForAfterUnsafePark(parkBlocker)
  }

  // To be consistent with com.sun.jdi.ThreadReference#ownedMonitors we do not include monitors
  // relinquished through Object.wait() in the list of owned monitors.
  val waitingOn = thread.waitingOn
  val isInObjectWait = waitingOn != null && thread.stack.any { it.contains("java.lang.Object.wait") }

  for (monitorInfo in thread.monitorsOwned) {
    for (lock in monitorInfo.locks) {
      if (isInObjectWait && lock == waitingOn) continue
      addOwnedMonitorAtDepth(lock, monitorInfo.depth)
    }
  }
}

private fun ThreadState.addParkingToWaitForAfterUnsafePark(parkBlocker: String) {
  val currentStackTrace = stackTrace ?: return
  val lines = currentStackTrace.lines().toMutableList()
  val unsafeParkFrameIndex = lines.indexOfFirst { it.contains(UNSAFE_PARK_FRAME) }
  if (unsafeParkFrameIndex < 0) return
  lines.add(unsafeParkFrameIndex + 1, "\t- parking to wait for  <$parkBlocker>")
  setStackTrace(lines.joinToString("\n"), isEmptyStackTrace)
}

@Serializable
private data class JcmdDump(
  val threadDump: JcmdThreadDump,
)

@Serializable
private data class JcmdThreadDump(
  val threadContainers: List<JcmdContainer>,
)

@Serializable
private data class JcmdContainer(
  val container: String = "",
  val parent: String? = null,
  val owner: String? = null,
  val threads: List<JcmdThread> = emptyList(),
)

@Serializable
private data class JcmdThread(
  val name: String = "",
  val tid: String = "",
  val virtual: Boolean? = null,
  val carrier: String? = null,
  val stack: List<String> = emptyList(),
  val state: String = "unknown",
  val blockedOn: String? = null,
  val waitingOn: String? = null,
  val parkBlocker: JcmdParkBlocker? = null,
  val monitorsOwned: List<JcmdMonitorInfo> = emptyList(),
)

@Serializable
private data class JcmdParkBlocker(
  @SerialName("object") val objectRef: String? = null,
)

@Serializable
private data class JcmdMonitorInfo(
  val depth: Int = -1,
  val locks: List<String> = emptyList(),
)
