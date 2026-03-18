// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.unscramble

import com.intellij.unscramble.IntelliJThreadDumpMetadata.Container
import com.intellij.unscramble.IntelliJThreadDumpMetadata.ThreadDumpMetadata
import com.intellij.unscramble.IntelliJThreadDumpMetadata.TreeLink
import org.jetbrains.annotations.ApiStatus

private const val IMPORT_HINT_COMMENT: String =
  "# This dump may be opened in IntelliJ IDEA using Analyze Stack Trace or Thread Dump..."

/**
 * Serializes dump items into a plain-text thread dump body followed by IntelliJ-specific metadata.
 */
@ApiStatus.Internal
fun serializeIntelliJThreadDump(dumpItems: List<DumpItem>, additionalComments: List<String> = emptyList()): String {
  val contentText = serializeThreadDumpBody(dumpItems)
  val metadataText = IntelliJThreadDumpMetadata.serialize(createMetadata(dumpItems))
  return buildString {
    appendLine(IMPORT_HINT_COMMENT)
    for (comment in additionalComments) {
      append("# ")
      appendLine(comment)
    }
    appendLine()
    if (contentText.isNotEmpty()) {
      append(contentText)
    }
    appendLine(IntelliJThreadDumpMetadata.META_DATA_MARKER)
    append(metadataText)
  }
}

/**
 * Collects the non-textual structure that has to be restored on import.
 */
private fun createMetadata(dumpItems: List<DumpItem>): ThreadDumpMetadata {
  val threadLinks = dumpItems
    .mapNotNull { dumpItem ->
      val treeId = serializeTreeId(dumpItem.treeId) ?: return@mapNotNull null
      val parentTreeId = serializeTreeId(dumpItem.parentTreeId) ?: return@mapNotNull null

      TreeLink(
        treeId = treeId,
        parentTreeId = parentTreeId,
      )
    }

  val containers = dumpItems
    .filter { it.isContainer }
    .mapNotNull { dumpItem ->
      val treeId = serializeTreeId(dumpItem.treeId) ?: return@mapNotNull null
      Container(
        name = dumpItem.name,
        treeId = treeId,
      )
    }

  return ThreadDumpMetadata(
    threadLinks = threadLinks,
    containers = containers,
  )
}

/**
 * Serialize [dumpItems] by concatenating their exported stack traces.
 */
private fun serializeThreadDumpBody(dumpItems: List<DumpItem>): String {
  return buildString {
    for (dumpItem in dumpItems) {
      if (dumpItem.exportedStackTrace.isBlank()) {
        continue
      }
      append(dumpItem.exportedStackTrace.trim())
      appendLine()
      appendLine()
    }
  }
}

private fun serializeTreeId(treeId: Long?): Long? {
  return treeId
}
