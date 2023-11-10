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
import java.util.concurrent.ConcurrentHashMap

private val metricIdBadCharacters: Regex = Regex("[^\\w.-]+")

class ParsingDiagnosticsHandlerImpl : ParserDiagnosticsHandler {
  private val meter = TelemetryManager.getInstance().getMeter(PlatformMetrics)
  private val sizeCounters: MutableMap<String, LongCounter> = ConcurrentHashMap()
  private val timeCounters: MutableMap<String, LongCounter> = ConcurrentHashMap()

  private fun sizeCounter(key: String): LongCounter = sizeCounters.computeIfAbsent(key) { meter.counterBuilder(it).setUnit("byte").build() }
  private fun timeCounter(key: String): LongCounter = timeCounters.computeIfAbsent(key) { meter.counterBuilder(it).setUnit("ns").build() }

  override fun registerParse(builder: PsiBuilder, language: Language, parsingTimeNs: Long) {
    val languageId = language.id.replace(metricIdBadCharacters, ".")

    var effectiveBuilder = builder
    while(effectiveBuilder is PsiBuilderAdapter){
      effectiveBuilder = effectiveBuilder.delegate
    }

    val textLength = effectiveBuilder.originalText.length.toLong()

    if(effectiveBuilder is PsiBuilderImpl && effectiveBuilder.lexingTimeNs > 0){
      sizeCounter("lexer.$languageId.size.bytes").add(textLength)
      timeCounter("lexer.$languageId.time.ns").add(effectiveBuilder.lexingTimeNs)
    }

    sizeCounter("parser.$languageId.size.bytes").add(textLength)
    timeCounter("parser.$languageId.time.ns").add(parsingTimeNs)
  }
}