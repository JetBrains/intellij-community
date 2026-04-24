// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.unscramble

import org.jetbrains.annotations.ApiStatus

private const val IMPORT_HINT_COMMENT: String =
  "# This dump may be opened in IntelliJ IDEA using Analyze Stack Trace or Thread Dump..."

/**
 * Serializes dump items into a plain-text thread dump body with IntelliJ import hint comments.
 */
@ApiStatus.Internal
fun serializeIntelliJThreadDump(dumpItems: List<DumpItem>, additionalComments: List<String> = emptyList()): String {
  val contentText = serializeThreadDumpBody(dumpItems)
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
  }
}

/**
 * Serialize [dumpItems] by concatenating their serialized exported representation.
 */
private fun serializeThreadDumpBody(dumpItems: List<DumpItem>): String {
  return dumpItems
    .asSequence()
    .map { it.serialize().trim() }
    .filter { it.isNotBlank() }
    .joinToString(separator = "\n\n")
}
