// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.diagnostic.freezeAnalyzer

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.diogen.analysis.freeze.ThreadDumpParser as DiogenThreadDumpParser

@ApiStatus.Internal
object FreezeAnalyzer {

  /**
   * Analyze freeze based on the IJ Platform knowledge and try to infer the relevant message.
   * If analysis fails, it returns `null`.
   */
  fun analyzeFreeze(threadDump: String, testName: String? = null): FreezeAnalysisResult? {
    val threadDumpParsed = runCatching { DiogenThreadDumpParser.parse(threadDump) }.getOrNull() ?: return null
    return DiogenFreezeAnalysisAdapter.analyze(threadDumpParsed, testName)
  }
}

@ApiStatus.Internal
data class FreezeAnalysisResult(val message: String, val threads: List<FreezeAnalysisThread>, val additionalMessage: String? = null)

@ApiStatus.Internal
data class FreezeAnalysisThread(val stackTrace: String)