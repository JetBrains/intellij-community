// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.diagnostic.telemetry

import io.opentelemetry.api.metrics.MeterProvider
import io.opentelemetry.context.Context
import io.opentelemetry.sdk.trace.ReadWriteSpan
import io.opentelemetry.sdk.trace.ReadableSpan
import io.opentelemetry.sdk.trace.SpanProcessor
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus.Internal
import kotlin.time.Duration.Companion.seconds

@Internal
class AggregatedSpanProcessor(private val mainScope: CoroutineScope) : SpanProcessor {
  private var batchSpanProcessor: BatchSpanProcessor? = null

  fun addSpansExporters(exporters: List<AsyncSpanExporter>) {
    batchSpanProcessor = BatchSpanProcessor(mainScope, exporters.toList(), MeterProvider.noop(), 5.seconds, 2048, 512, 30.seconds)
  }

  override fun onStart(parentContext: Context, span: ReadWriteSpan) {
    batchSpanProcessor?.onStart(parentContext, span)
  }

  override fun isStartRequired(): Boolean = false

  override fun onEnd(span: ReadableSpan) {
    batchSpanProcessor?.onEnd(span)
  }

  override fun isEndRequired(): Boolean = true
}