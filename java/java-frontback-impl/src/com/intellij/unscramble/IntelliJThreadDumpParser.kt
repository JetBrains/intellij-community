// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.unscramble

import com.intellij.openapi.util.text.StringUtil
import com.intellij.threadDumpParser.ThreadDumpParser
import com.intellij.threadDumpParser.ThreadState
import org.jetbrains.annotations.ApiStatus

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
 * Parses thread dump text and restores thread/container hierarchy from inline metadata when present.
 */
@ApiStatus.Internal
fun parseIntelliJThreadDump(text: String): ThreadDumpState {
  val normalizedText = StringUtil.convertLineSeparators(text)
  val parsedThreadStates = ThreadDumpParser.parse(normalizedText)
  val threadStates = mutableListOf<ThreadState>()
  val threadContainerDescriptors = mutableListOf<JavaThreadContainerDesc>()
  for (threadState in parsedThreadStates) {
    if (threadState.type == IntelliJThreadDumpMetadata.CONTAINER_TYPE) {
      val containerId = threadState.uniqueId ?: continue
      threadContainerDescriptors.add(
        JavaThreadContainerDesc(
          name = threadState.name,
          containerId = containerId,
          parentId = threadState.threadContainerUniqueId,
        ),
      )
    }
    else {
      threadStates.add(threadState)
    }
  }
  return ThreadDumpState(threadStates, threadContainerDescriptors)
}
