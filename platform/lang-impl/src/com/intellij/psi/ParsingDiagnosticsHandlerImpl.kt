// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi

import com.intellij.lang.Language
import com.intellij.lang.PsiBuilder
import com.intellij.lang.impl.PsiBuilderAdapter
import com.intellij.lang.impl.PsiBuilderImpl
import com.intellij.platform.diagnostic.telemetry.PlatformMetrics
import com.intellij.platform.diagnostic.telemetry.TelemetryManager
import com.intellij.psi.ParsingDiagnostics.ParserDiagnosticsHandler
import io.opentelemetry.api.metrics.LongCounter
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.ConcurrentHashMap

private val metricIdBadCharacters: Regex = Regex("[^\\w.-]+")

@ApiStatus.Internal
class ParsingDiagnosticsHandlerImpl : ParserDiagnosticsHandler {
  private val meter = TelemetryManager.getInstance().getMeter(PlatformMetrics)
  private val diagnosticsEntries: MutableMap<String, DiagnosticsEntry> = ConcurrentHashMap()

  override fun registerParse(builder: PsiBuilder, language: Language, parsingTimeNs: Long) {
    var effectiveBuilder = builder
    while(effectiveBuilder is PsiBuilderAdapter){
      effectiveBuilder = effectiveBuilder.delegate
    }

    val textLength = effectiveBuilder.originalText.length.toLong()

    diagnosticsEntries.computeIfAbsent(language.id) { DiagnosticsEntry(it) }.apply {
      if (effectiveBuilder is PsiBuilderImpl && effectiveBuilder.lexingTimeNs > 0) {
        lexerSizeCounter.add(textLength)
        lexerTimeCounter.add(effectiveBuilder.lexingTimeNs)
      }

      parserSizeCounter.add(textLength)
      parserTimeCounter.add(parsingTimeNs)
    }
  }

  private inner class DiagnosticsEntry(languageId: String) {
    val lexerSizeCounter: LongCounter
    val lexerTimeCounter: LongCounter
    val parserSizeCounter: LongCounter
    val parserTimeCounter: LongCounter

    init {
      val sanitizedId = languageId.replace(metricIdBadCharacters, ".")
      meter.apply {
        lexerSizeCounter = counterBuilder("lexer.$sanitizedId.size.bytes").setUnit("byte").build()
        lexerTimeCounter = counterBuilder("lexer.$sanitizedId.time.ns").setUnit("ns").build()
        parserSizeCounter = counterBuilder("parser.$sanitizedId.size.bytes").setUnit("byte").build()
        parserTimeCounter = counterBuilder("parser.$sanitizedId.time.ns").setUnit("ns").build()
      }
    }
  }

}