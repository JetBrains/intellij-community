// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.diagnostic.freezeAnalyzer

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.diogen.analysis.freeze.FreezeMessageFormatter
import org.jetbrains.diogen.analysis.freeze.ThreadDumpParser
import org.jetbrains.diogen.analysis.model.XStackFrame
import org.jetbrains.diogen.analysis.model.parseFrame
import org.jetbrains.diogen.analysis.freeze.FreezeAnalyzer as DiogenFreezeAnalyzer

@ApiStatus.Internal
object FreezeAnalyzer {

  /**
   * Analyze freeze based on the IJ Platform knowledge and try to infer the relevant message.
   * If analysis fails, it returns `null`.
   *
   * The analysis and message generation are implemented in the `diogen-analysis` library;
   * this is a thin wrapper adapting its result to the platform API.
   */
  fun analyzeFreeze(threadDump: String, testName: String? = null): FreezeAnalysisResult? =
    FreezeMessageFormatter.analyzeAndFormat(threadDump, testName)?.let { result ->
      FreezeAnalysisResult(result.message, result.threads.map(::FreezeAnalysisThread), result.additionalMessage)
    }

  /**
   * Analyze freeze and locate the thread causing it.
   * Returns the topmost method considered responsible for the freeze and the stack frames of the causing thread
   * starting from that method (frames that cannot be parsed are omitted).
   * If the causing thread cannot be determined, returns `null`.
   */
  fun analyzeFreezeCause(threadDump: String): FreezeCauseResult? {
    val freezeCause = DiogenFreezeAnalyzer.analyzeFreeze(ThreadDumpParser.parse(threadDump))
    val cause = freezeCause.cause ?: return null
    val topCallable = DiogenFreezeAnalyzer.selectCallable(cause) ?: return null

    val lines = cause.lines
    val startIndex = lines.indexOfFirst { line ->
      line.toString().trim().removePrefix("at ").startsWith(topCallable)
    }
    if (startIndex < 0) return null

    val stackFrames = (startIndex until lines.size).mapNotNull { parseStackTraceElement(lines[it]) }
    return FreezeCauseResult(topCallable, stackFrames)
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
}

@ApiStatus.Internal
data class FreezeAnalysisResult(val message: String, val threads: List<FreezeAnalysisThread>, val additionalMessage: String? = null)

@ApiStatus.Internal
data class FreezeAnalysisThread(val stackTrace: String)

/**
 * The topmost method considered responsible for a freeze and the causing thread's stack frames starting from it.
 */
@ApiStatus.Internal
data class FreezeCauseResult(val topCallable: String, val stackFrames: List<StackTraceElement>)