// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.unscramble

import com.intellij.openapi.util.text.StringUtil
import com.intellij.threadDumpParser.ThreadDumpParser
import com.intellij.threadDumpParser.ThreadState
import com.intellij.unscramble.IntelliJThreadDumpMetadata.ThreadDumpMetadata
import org.jetbrains.annotations.ApiStatus

private val serializedThreadNamePattern = Regex("^(.+)@(-?\\d+)$")

/**
 * Parsed IntelliJ thread dump payload represented as plain thread states and container descriptors.
 */
@ApiStatus.Internal
data class ThreadDumpState(
  val threadStates: List<ThreadState>,
  val threadContainerDescriptors: List<JavaThreadContainerDesc>,
)

/**
 * Converts parsed threads and containers into dump items consumed by the thread dump UI.
 */
@ApiStatus.Internal
fun ThreadDumpState.dumpItems(): List<MergeableDumpItem> {
  return toDumpItems(threadStates, threadContainerDescriptors)
}

/**
 * Parses the IntelliJ thread dump format and restores thread/container hierarchy from the metadata footer.
 */
@ApiStatus.Internal
fun parseIntelliJThreadDump(text: String): ThreadDumpState? {
  val payload = parseIntelliJThreadDumpPayload(text) ?: return null
  val metadata = IntelliJThreadDumpMetadata.parse(payload.rawMetadata)
  val threadStates = ThreadDumpParser.parse(payload.rawThreads)
  processUniqueIds(threadStates)
  applyThreadMetadata(threadStates, metadata)
  return ThreadDumpState(threadStates, createThreadContainerDescriptors(metadata))
}

/**
 * Splits given Thread Dump [text] to raw threads and raw metadata strings.
 */
private fun parseIntelliJThreadDumpPayload(text: String): RawIntelliJThreadDump? {
  val normalizedText = StringUtil.convertLineSeparators(text)
  val markerIndex = normalizedText.lastIndexOf(IntelliJThreadDumpMetadata.META_DATA_MARKER)
  if (markerIndex < 0) {
    return null
  }
  if (markerIndex > 0 && normalizedText[markerIndex - 1] != '\n') {
    return null
  }
  val rawThreads = normalizedText.substring(0, markerIndex).trimEnd('\n', '\r')
  val rawMetadata = normalizedText.substring(markerIndex + IntelliJThreadDumpMetadata.META_DATA_MARKER.length)
  return RawIntelliJThreadDump(
    rawThreads = rawThreads,
    rawMetadata = rawMetadata,
  )
}

private data class RawIntelliJThreadDump(
  val rawThreads: String,
  val rawMetadata: String,
)

/**
 * Restores serialized `@<tree_id>` suffix from IntelliJ-formatted thread names into [ThreadState.uniqueId]
 */
private fun processUniqueIds(threadStates: List<ThreadState>) {
  for (threadState in threadStates) {
    val match = serializedThreadNamePattern.matchEntire(threadState.name) ?: continue
    threadState.uniqueId = match.groupValues[2].toLong()
  }
}

/**
 * Builds hierarchy from given [threadStates] using [metadata] by modifying [ThreadState.threadContainerUniqueId].
 */
private fun applyThreadMetadata(
  threadStates: List<ThreadState>,
  metadata: ThreadDumpMetadata?,
) {
  val parentIds = metadata?.threadLinks.orEmpty().associate { it.treeId to it.parentTreeId }
  val containerIds = metadata?.containers.orEmpty().mapTo(linkedSetOf()) { it.treeId }
  for (threadState in threadStates) {
    val treeId = threadState.uniqueId
    if (treeId == 0L) continue
    val parentTreeId = parentIds[treeId] ?: continue
    if (parentTreeId !in containerIds) continue
    threadState.threadContainerUniqueId = parentTreeId
  }
}

/**
 * Restores container descriptors stored in metadata so hierarchy can be restored.
 */
private fun createThreadContainerDescriptors(
  metadata: ThreadDumpMetadata?,
): List<JavaThreadContainerDesc> {
  if (metadata == null) {
    return emptyList()
  }
  val containers = metadata.containers
  if (containers.isEmpty()) {
    return emptyList()
  }
  val parentIds = metadata.threadLinks.associate { it.treeId to it.parentTreeId }
  return containers.map { container ->
    JavaThreadContainerDesc(
      name = container.name,
      containerId = container.treeId,
      parentContainerId = parentIds[container.treeId],
    )
  }
}
