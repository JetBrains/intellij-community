// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.unscramble

import com.intellij.diagnostic.COROUTINE_DUMP_HEADER
import com.intellij.diagnostic.COROUTINE_DUMP_HEADER_STRIPPED
import com.intellij.openapi.util.text.StringUtil
import com.intellij.threadDumpParser.ThreadState

private val COROUTINE_HEADER_PATTERN: Regex = """^-(?:\[x(?<count>\d+) of])?\s+(?:"(?<name>[^"]+)":)?(?<job>.*?)(?:, state: (?<state>\S+))?(?: \[(?<context>.+)])?$""".toRegex()
private const val COROUTINE_ROOT_ID: Long = Long.MIN_VALUE

internal fun parseCoroutineDump(text: String?): List<ThreadState>? {
  if (text == null) return null
  val lines = StringUtil.convertLineSeparators(text).lines()
  val headerIndex = lines.indexOfFirst(::isCoroutineDumpHeader)
  if (headerIndex < 0) return null
  val coroutineLines = mutableListOf<String>()
  val otherDiagnostics = mutableListOf<String>()
  for (i in headerIndex until lines.size) {
    val line = lines[i]
    if (otherDiagnostics.isNotEmpty() || (i > headerIndex && isOtherDiagnosticDumpHeader(line))) {
      otherDiagnostics.add(line)
    } else {
      coroutineLines.add(line)
    }
  }
  val coroutines = parseCoroutineNodes(coroutineLines)
  if (coroutines.isEmpty()) return null
  return buildList {
    if (coroutines.isNotEmpty()) {
      add( createCoroutineRootThreadState())
      addAll(coroutines.map { it.toThreadState() })
    }
    if (otherDiagnostics.isNotEmpty()) {
      add(createOtherDiagnosticsThreadState(otherDiagnostics))
    }
  }
}

private fun parseCoroutineNodes(lines: List<String>): List<CoroutineNode> {
  val nodes = mutableListOf<CoroutineNode>()
  val parentStack = mutableListOf<CoroutineNode>()
  var currentNode: CoroutineNode? = null
  var nextId = COROUTINE_ROOT_ID + 1

  for (line in lines.drop(1)) { // skip header line
    if (line.isBlank()) continue
    val header = parseCoroutineHeader(line)
    if (header != null) {
      while (parentStack.size > header.indentLevel) {
        parentStack.removeLast()
      }
      val parentTreeId = parentStack.lastOrNull()?.treeId ?: COROUTINE_ROOT_ID
      val node = CoroutineNode(
        header = header,
        treeId = nextId++,
        parentTreeId = parentTreeId
      )
      nodes.add(node)
      parentStack.add(node)
      currentNode = node
      currentNode.stackFrames.add(line)
    } else {
      val stackFrame = currentNode?.let { parseStackFrame(line, it.header.indentLevel) }
      if (stackFrame != null) {
        currentNode.stackFrames.add(stackFrame)
      }
    }
  }
  return nodes
}

private fun parseCoroutineHeader(line: String): CoroutineHeader? {
  val indentLevel = line.indentLevel()
  val content = line.drop(indentLevel)
  val match = COROUTINE_HEADER_PATTERN.matchEntire(content) ?: return null
  val count = match.groups["count"]?.value?.toIntOrNull() ?: 1
  val name = match.groups["name"]?.value
  val job = match.groups["job"]?.value
  val state = match.groups["state"]?.value
  val context = match.groups["context"]?.value

  return CoroutineHeader(
    indentLevel = indentLevel,
    name = (name?.let { "\"${it}\":" } ?: "") + job,
    state = state ?: "UNKNOWN",
    metadata = buildMap {
      context?.let { put("dispatcher", it) }
      job?.let { put("job", it) }
    },
    similarCoroutinesCount = count.coerceAtLeast(1),
  )
}

private fun parseStackFrame(line: String, ownerIndentLevel: Int): String? {
  if (line.length <= ownerIndentLevel) return null
  val frame = line.substring(ownerIndentLevel)
  if (!frame.startsWith("\tat ")) return null
  return frame
}

private fun String.indentLevel(): Int = indexOfFirst { it != '\t' }.coerceAtLeast(0)

private fun isCoroutineDumpHeader(line: String): Boolean {
  return line == COROUTINE_DUMP_HEADER || line == COROUTINE_DUMP_HEADER_STRIPPED
}

private fun isOtherDiagnosticDumpHeader(line: String): Boolean {
  return line.startsWith("---------- ") && line.endsWith(" ----------") && !isCoroutineDumpHeader(line)
}

private fun createCoroutineRootThreadState(): ThreadState = ThreadState("Dumped Coroutines", "").also {
  it.type = IntelliJThreadDumpMetadata.COROUTINE_ROOT_TYPE
  it.uniqueId = COROUTINE_ROOT_ID
  it.setStackTrace("", true)
}

private fun createOtherDiagnosticsThreadState(otherDiagnostics: List<String>): ThreadState = ThreadState("Other diagnostics", "").also {
  it.setStackTrace(otherDiagnostics.joinToString("\n"), true)
}

private fun CoroutineNode.toThreadState(): ThreadState = ThreadState(header.name, header.state).also {
  it.type = IntelliJThreadDumpMetadata.COROUTINE_TYPE
  it.uniqueId = treeId
  it.threadContainerUniqueId = parentTreeId
  it.setMetadata(header.metadata)
  it.similarThreadsCount = header.similarCoroutinesCount
  it.setStackTrace(formatStackTrace(), stackFrames.isEmpty())
}

private fun CoroutineNode.formatStackTrace(): String =
  stackFrames.joinToString("\n").dropWhile { it == '\t' }

private data class CoroutineHeader(
  val indentLevel: Int,
  val name: String,
  val state: String,
  val metadata: Map<String, String>,
  val similarCoroutinesCount: Int,
)

private data class CoroutineNode(
  val header: CoroutineHeader,
  val treeId: Long,
  val parentTreeId: Long,
  val stackFrames: MutableList<String> = mutableListOf(),
)