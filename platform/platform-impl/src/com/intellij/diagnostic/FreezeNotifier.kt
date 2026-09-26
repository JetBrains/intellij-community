// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.PluginUtils
import com.intellij.openapi.extensions.PluginId
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.diogen.analysis.freeze.ThreadDumpParser
import org.jetbrains.diogen.analysis.model.XStackFrame
import org.jetbrains.diogen.analysis.model.parseFrame
import org.jetbrains.diogen.analysis.freeze.FreezeAnalyzer as DiogenFreezeAnalyzer

@ApiStatus.Internal
interface FreezeNotifier {
  suspend fun notifyFreeze(
    event: LogMessage,
    problematicPluginId: PluginId,
    currentDumps: Collection<ThreadDump>,
    durationMs: Long,
  )
}

@ApiStatus.Internal
data class FreezeCauseResult(
  val plugin: PluginId?,
  val stackFrame: String?
)

internal fun analyzeFreezeCausingPlugin(dump: String): FreezeCauseResult? {
  val freezeCause = analyzeFreezeCause(dump) ?: return null
  for (element in freezeCause.second) {
    val descriptor = PluginUtils.getPluginDescriptorOrPlatformByClassName(element.className) ?: continue
    if (descriptor.pluginId == PluginManagerCore.CORE_ID) continue

    return FreezeCauseResult(descriptor.pluginId, freezeCause.first)
  }
  return FreezeCauseResult(null, freezeCause.first)
}

/**
 * Analyze freeze and locate the thread causing it.
 * Returns the topmost method considered responsible for the freeze and the stack frames of the causing thread
 * starting from that method (frames that cannot be parsed are omitted).
 * If the causing thread cannot be determined, returns `null`.
 */
private fun analyzeFreezeCause(threadDump: String): Pair<String, List<StackTraceElement>>? {
  val freezeCause = DiogenFreezeAnalyzer.analyzeFreeze(ThreadDumpParser.parse(threadDump))
  val cause = freezeCause.cause ?: return null
  val topCallable = DiogenFreezeAnalyzer.selectCallable(cause) ?: return null

  val lines = cause.lines
  val startIndex = lines.indexOfFirst { line ->
    line.toString().trim().removePrefix("at ").startsWith(topCallable)
  }
  if (startIndex < 0) return null

  val stackFrames = (startIndex until lines.size).mapNotNull { parseStackTraceElement(lines[it]) }
  return topCallable to stackFrames
}

private fun parseStackTraceElement(stackTrace: CharSequence): StackTraceElement? {
  val frame = parseFrame(stackTrace.toString().trim(), false) as? XStackFrame.Callable ?: return null
  val methodSeparator = frame.name.lastIndexOf('.')
  if (methodSeparator <= 0 || methodSeparator == frame.name.lastIndex) return null
  return StackTraceElement(
    frame.name.substring(0, methodSeparator),
    frame.name.substring(methodSeparator + 1),
    frame.source?.fileName,
    frame.source?.line ?: -1,
  )
}
