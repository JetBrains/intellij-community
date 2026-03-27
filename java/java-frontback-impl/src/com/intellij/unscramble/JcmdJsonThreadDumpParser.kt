// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.unscramble

import com.intellij.threadDumpParser.ThreadDumpParser
import com.intellij.threadDumpParser.ThreadState
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.annotations.ApiStatus

private val jcmdJson = Json { ignoreUnknownKeys = true }

/**
 * Parses the output of `jcmd <pid> Thread.dump_to_file -format=json` preserving the hierarchy of thread containers.
 *
 * Returns `null` if the text is not a jcmd JSON thread dump.
 */
@ApiStatus.Internal
fun parseJcmdJsonThreadDump(text: String): ThreadDumpState? {
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

  for (threadState in threadStates) {
    ThreadDumpParser.inferThreadStateDetail(threadState)
  }

  ThreadDumpParser.detectWaitingAndDeadlockedThreads(threadStates)

  ThreadDumpParser.sortThreads(threadStates)

  return ThreadDumpState(threadStates, containerDescriptors)
}

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
  contendedMonitor = thread.blockedOn ?: thread.waitingOn

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
  val monitorsOwned: List<JcmdMonitorInfo> = emptyList(),
)

@Serializable
private data class JcmdMonitorInfo(
  val depth: Int = -1,
  val locks: List<String> = emptyList(),
)
